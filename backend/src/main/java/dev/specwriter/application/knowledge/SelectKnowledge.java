package dev.specwriter.application.knowledge;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.knowledge.AdrNumber;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSelection.ImpactedModule;
import dev.specwriter.domain.knowledge.MentionKind;
import dev.specwriter.domain.knowledge.Mentionable;
import dev.specwriter.domain.knowledge.Mentions;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.session.Session;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Sélection de la connaissance d'une session (research.md R24) : la fiche générale toujours ; le
 * vocabulaire et la vue d'ensemble (3 000 caractères) ; 4 ADR au plus (6 000), un ADR remplacé
 * l'étant par son successeur et un ADR obsolète écarté ; 3 tickets (3 000) ; pour une
 * spécification, les faits d'architecture (3 000) ; 2 documents d'un dossier synchronisé, relus
 * entiers (8 000, 006). Le tout tient en 35 000 caractères. La recherche part du besoin et des
 * réponses : la sélection suit leurs changements.
 */
public class SelectKnowledge implements SessionKnowledge {

    static final int MAX_ROLES = 3_000;
    static final int MAX_ADRS = 4;
    static final int MAX_ADR_TEXT = 12_000;
    /** Au plus par ADR : au-delà, l'ADR est coupé à la fin d'une section (004, research.md R4). */
    static final int MAX_ADR_PART = 8_000;
    static final String ADR_TRUNCATED = "\n\n[ADR tronqué]";
    /** Documents d'un dossier synchronisé (006, research.md R6) : relus entiers, coupés à une section. */
    static final int MAX_DOCUMENTS = 2;
    static final int MAX_DOCUMENT_TEXT = 8_000;
    static final int MAX_DOCUMENT_PART = 6_000;
    static final String DOCUMENT_TRUNCATED = "\n\n[document tronqué]";
    /** Documents désignés avec @ (007, research.md R6) : transmis d'office, avant la recherche. */
    static final int MAX_MENTIONED = 4;
    static final int MAX_MENTIONED_TEXT = 12_000;
    static final int MAX_MENTIONED_PART = 8_000;
    static final int MAX_TICKETS = 3;
    static final int MAX_TICKET_TEXT = 3_000;
    static final int MAX_ARCHITECTURE = 3_000;
    static final int MAX_SUBTASKS = 15;
    static final int MAX_SUBTASK_TEXT = 1_500;
    static final int MAX_QUERY = KnowledgeQueries.MAX_QUERY;
    /** Taille comptée pour un module, un échange ou un constat, en plus de ses noms. */
    private static final int ARCHITECTURE_MARKUP = 40;
    private static final int ADR_CANDIDATES = 12;
    /** Tickets lus par recherche avant la fusion (hors famille de la story liée). */
    private static final int TICKET_CANDIDATES = 10;
    /** Passages lus par recherche : les sections Antora sont dans la même famille et écartées ensuite. */
    private static final int DOCUMENT_CANDIDATES = 20;
    private static final String DOCUMENTS_PREFIX = "docs:";
    private static final int RRF_K = 60;
    private static final int SUCCESSION = 5;

    private static final Logger LOG = Logger.getLogger(SelectKnowledge.class);

    private final ProjectRepository projects;
    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final AnalyzeArchitecture analyze;
    private final StoryFamily families;
    /** Sans eux (null), un document n'est pas relu : ses passages indexés servent. */
    private final DocSourceRepository docSources;
    private final DocumentFolder folder;

    public SelectKnowledge(ProjectRepository projects, KnowledgeIndex index, EmbeddingPort embedding,
            AnalyzeArchitecture analyze, StoryFamily families, DocSourceRepository docSources, DocumentFolder folder) {
        this.projects = projects;
        this.index = index;
        this.embedding = embedding;
        this.analyze = analyze;
        this.families = families;
        this.docSources = docSources;
        this.folder = folder;
    }

    public SelectKnowledge(ProjectRepository projects, KnowledgeIndex index, EmbeddingPort embedding,
            AnalyzeArchitecture analyze, StoryFamily families) {
        this(projects, index, embedding, analyze, families, null, null);
    }

    public SelectKnowledge(ProjectRepository projects, KnowledgeIndex index, EmbeddingPort embedding,
            AnalyzeArchitecture analyze) {
        this(projects, index, embedding, analyze, StoryFamily.none());
    }

    @Override
    public ProjectKnowledge forQuestions(Session session, List<QuestionAndAnswer> answers) {
        return select(session, answers, null, null, false);
    }

    @Override
    public ProjectKnowledge forSpecification(Session session, List<QuestionAndAnswer> answers) {
        return select(session, answers, null, null, true);
    }

    @Override
    public ProjectKnowledge forEdit(Session session, List<QuestionAndAnswer> answers, String instruction,
            String currentMarkdown) {
        return select(session, answers, instruction, currentMarkdown, true);
    }

    private ProjectKnowledge select(Session session, List<QuestionAndAnswer> answers, String instruction,
            String currentMarkdown, boolean architecture) {
        ProjectKnowledge sheet = SessionKnowledge.sheet(projects, session);
        String query = query(instruction, session.need().text(), answers);
        Set<Integer> cited = cited(session.need().text(), answers, instruction, currentMarkdown);
        Search combined = search(query);
        float[] vector = combined.vector();
        String model = combined.model();
        // en modification, l'instruction est aussi cherchée seule : noyée dans le besoin, elle ne
        // ferait pas remonter le ticket ou l'ADR qu'elle désigne (004, US4)
        List<Search> searches = instruction == null || instruction.isBlank() ? List.of(combined)
                : List.of(search(query(instruction, "", List.of())), combined);
        StoryFamily.Family family = families.of(session);
        List<ProjectKnowledge.Entry> entries = new ArrayList<>(roles(session.projectId()));
        // les documents désignés avec @ passent avant la recherche, après la fiche générale (007)
        List<ProjectKnowledge.Entry> mentioned = mentioned(session.projectId(), session.need().text(),
                answers.stream().map(QuestionAndAnswer::answer).toList(), instruction,
                Math.min(MAX_MENTIONED_TEXT, left(sheet, entries)));
        entries.addAll(mentioned);
        Set<String> designated = new LinkedHashSet<>(mentioned.stream().map(ProjectKnowledge.Entry::ref).toList());
        // la fiche générale l'emporte : les ADR prennent ce qui reste sous le plafond (004, FR-017)
        int adrBudget = Math.min(MAX_ADR_TEXT, left(sheet, entries));
        entries.addAll(adrs(session, searches, cited, adrBudget, designated));
        // les documents prennent ce que laissent la fiche et les ADR (006, research.md R6)
        int documentBudget = Math.min(MAX_DOCUMENT_TEXT, left(sheet, entries));
        entries.addAll(documents(session, searches, documentBudget, designated));
        entries.addAll(tickets(session, family, searches));
        ProjectKnowledge selected = new ProjectKnowledge(sheet.generalSheet(), sheet.sheetUpdatedAt(), entries,
                List.of(), List.of(), List.of(), subtasks(session, family));
        if (!architecture) {
            return selected;
        }
        try {
            AnalyzeArchitecture.Analysis a = analyze.run(session.id(), session.projectId(), session.language(),
                    session.need().text(), answers, query, vector, model);
            return bounded(selected.withArchitecture(a.modules(), a.exchanges(), a.facts()));
        } catch (DomainException e) {
            // la rédaction continue sans les faits d'architecture ; l'analyse sera retentée
            LOG.warnf("Connaissance : analyse d'architecture indisponible (%s)", e.code());
            return selected;
        }
    }

    /**
     * Place des documents désignés pour une fiche générale donnée, calculée comme à la génération :
     * l'état annoncé des mentions correspond à leur transmission (007, research.md R8).
     */
    int mentionBudget(UUID projectId, String generalSheet) {
        return Math.min(MAX_MENTIONED_TEXT, left(ProjectKnowledge.sheet(generalSheet, null), roles(projectId)));
    }

    /** Taille au plus d'un document désigné : le premier coupé à la place restante, les suivants entiers ou rien. */
    static int mentionPart(boolean first, int left) {
        return first ? Math.min(MAX_MENTIONED_PART, left) : MAX_MENTIONED_PART;
    }

    /** Place restante sous le plafond, réserves d'architecture, de tickets et de sous-tâches déduites. */
    private static int left(ProjectKnowledge sheet, List<ProjectKnowledge.Entry> entries) {
        return ProjectKnowledge.MAX_TOTAL - MAX_ARCHITECTURE - sheet.generalSheet().length() - size(entries)
                - MAX_TICKET_TEXT - MAX_SUBTASK_TEXT;
    }

    /**
     * Documents désignés avec @ dans le besoin, les réponses puis l'instruction (007, research.md
     * R6) : {@link #MAX_MENTIONED} au plus, {@link #MAX_MENTIONED_PART} chacun, le premier coupé s'il
     * le faut, les suivants seulement s'ils tiennent. Un ADR suit la voie des ADR (successeur d'un
     * remplacé, rien pour un obsolète) ; un document de dossier est relu (006) ; une page Antora est
     * recomposée de ses sections.
     */
    List<ProjectKnowledge.Entry> mentioned(UUID projectId, String need, List<String> answers, String instruction,
            int budget) {
        if (budget <= 0 || !Mentions.mayContain(need, instruction) && !Mentions.mayContain(answers.toArray(String[]::new))) {
            return List.of();
        }
        List<Mentionable> designated = Mentions.designated(need, answers, instruction, index.mentionables(projectId));
        List<KnowledgeItem> adrs = designated.stream().anyMatch(d -> d.kind() == MentionKind.ADR)
                ? index.all(projectId, KnowledgeKind.ADR) : List.of();
        List<ProjectKnowledge.Entry> chosen = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int left = budget;
        for (Mentionable d : designated) {
            int part = mentionPart(chosen.isEmpty(), left);
            Optional<ProjectKnowledge.Entry> entry = d.kind() == MentionKind.ADR ? designatedAdr(d, adrs, part)
                    : document(projectId, d.ref(), part);
            if (entry.isEmpty() || !seen.add(entry.get().ref())) {
                continue;
            }
            int size = entry.get().title().length() + entry.get().text().length();
            if (size > left) {
                break;
            }
            chosen.add(entry.get().withDesignated());
            left -= size;
            if (chosen.size() == MAX_MENTIONED) {
                break;
            }
        }
        return chosen;
    }

    /** ADR désigné, ou son successeur s'il est remplacé ; rien s'il est obsolète. */
    private static Optional<ProjectKnowledge.Entry> designatedAdr(Mentionable d, List<KnowledgeItem> all, int part) {
        return all.stream().filter(a -> a.ref().equals(d.ref()) && a.chunk() == 0).findFirst()
                .flatMap(a -> inForce(a, all)).map(current -> whole(current, all, part))
                .filter(e -> !e.text().isEmpty());
    }

    /** Une recherche : son texte et, si le modèle local est prêt, son vecteur. */
    private record Search(String query, float[] vector, String model) {
    }

    private Search search(String query) {
        if (embedding.state() == EmbeddingPort.State.READY) {
            try {
                return new Search(query, embedding.query(query), embedding.modelId());
            } catch (RuntimeException e) {
                // la recherche se rabat sur les termes (FR-043)
                LOG.warnf("Connaissance : vecteur de la recherche indisponible (%s)", e.getClass().getSimpleName());
            }
        }
        return new Search(query, null, null);
    }

    /** Résultats des recherches, fusionnés par rangs ({@link #fuse}). */
    private List<KnowledgeItem> hits(Session session, List<Search> searches, KnowledgeKind kind, int max) {
        return fuse(searches.stream().map(s -> index.search(session.projectId(), s.query(), s.vector(), s.model(),
                List.of(kind), max).stream().map(KnowledgeIndex.Hit::item).toList()).toList());
    }

    /**
     * Fusion par rangs réciproques, comme l'index entre termes et sens : chaque liste apporte
     * 1 / (60 + rang) à ses éléments (même élément, même morceau). Un élément trouvé par les deux
     * recherches passe devant le premier d'une seule ; à égalité, l'ordre des listes départage.
     */
    static List<KnowledgeItem> fuse(List<List<KnowledgeItem>> lists) {
        Map<String, KnowledgeItem> items = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();
        for (List<KnowledgeItem> list : lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                String key = list.get(rank).ref() + "#" + list.get(rank).chunk();
                items.putIfAbsent(key, list.get(rank));
                scores.merge(key, 1.0 / (RRF_K + rank + 1), Double::sum);
            }
        }
        return items.keySet().stream().sorted(Comparator.comparing(scores::get, Comparator.reverseOrder()))
                .map(items::get).toList();
    }

    /** Vocabulaire et vue d'ensemble, toujours transmis (FR-042), dans la limite de 3 000 caractères. */
    private List<ProjectKnowledge.Entry> roles(UUID projectId) {
        List<ProjectKnowledge.Entry> out = new ArrayList<>();
        int left = MAX_ROLES;
        for (KnowledgeItem item : index.withRole(projectId)) {
            int room = left - item.title().length();
            if (room <= 0) {
                break;
            }
            ProjectKnowledge.Entry e = entry(item, room);
            out.add(e);
            left -= item.title().length() + e.text().length();
        }
        return out;
    }

    /**
     * ADR cités (004, US4), puis ADR en vigueur les plus proches ; un remplacé cède la place à son
     * successeur, un obsolète est écarté (FR-036). Chaque ADR est transmis entier, morceaux dans
     * l'ordre (004, research.md R4) : {@link #MAX_ADR_PART} au plus par ADR, {@link #MAX_ADR_TEXT} au
     * total ; le premier qui ne tient plus arrête la liste.
     */
    private List<ProjectKnowledge.Entry> adrs(Session session, List<Search> searches, Set<Integer> cited, int budget,
            Set<String> designated) {
        List<KnowledgeItem> hits = hits(session, searches, KnowledgeKind.ADR, ADR_CANDIDATES);
        if (hits.isEmpty() && cited.isEmpty()) {
            return List.of();
        }
        List<KnowledgeItem> all = index.all(session.projectId(), KnowledgeKind.ADR);
        List<KnowledgeItem> candidates = new ArrayList<>();
        for (int number : cited) {
            all.stream().filter(a -> a.chunk() == 0 && AdrNumber.of(a.ref(), a.title()).equals(OptionalInt.of(number)))
                    .sorted(Comparator.comparing(KnowledgeItem::ref)).forEach(candidates::add);
        }
        candidates.addAll(hits);
        Set<String> seen = new LinkedHashSet<>(designated);
        List<ProjectKnowledge.Entry> chosen = new ArrayList<>();
        int left = budget;
        for (KnowledgeItem candidate : candidates) {
            Optional<KnowledgeItem> current = inForce(candidate, all);
            if (current.isEmpty() || !seen.add(current.get().ref())) {
                continue;
            }
            // le premier ADR passe toujours, coupé s'il le faut ; les suivants seulement entiers
            ProjectKnowledge.Entry entry = whole(current.get(), all,
                    chosen.isEmpty() ? Math.min(MAX_ADR_PART, left) : MAX_ADR_PART);
            int size = entry.title().length() + entry.text().length();
            if (size > left || entry.text().isEmpty()) {
                break;
            }
            chosen.add(entry);
            left -= size;
            if (chosen.size() == MAX_ADRS) {
                break;
            }
        }
        return chosen;
    }

    /** Numéros des ADR cités dans le besoin, les réponses, l'instruction et la version en cours. */
    private static Set<Integer> cited(String need, List<QuestionAndAnswer> answers, String instruction,
            String currentMarkdown) {
        Set<Integer> out = new LinkedHashSet<>();
        out.addAll(AdrNumber.cited(instruction));
        out.addAll(AdrNumber.cited(need));
        answers.forEach(a -> out.addAll(AdrNumber.cited(a.answer())));
        out.addAll(AdrNumber.cited(currentMarkdown));
        return out;
    }

    /**
     * Documents des dossiers synchronisés dont un passage est retenu, regroupés par document : chacun
     * relu entier dans son dossier, sinon recomposé de ses passages indexés ; {@link #MAX_DOCUMENT_PART}
     * au plus par document, le premier coupé s'il le faut, le suivant seulement s'il tient. Un ADR de
     * ces dossiers suit la voie des ADR.
     */
    private List<ProjectKnowledge.Entry> documents(Session session, List<Search> searches, int budget,
            Set<String> designated) {
        if (budget <= 0) {
            return List.of();
        }
        List<String> refs = hits(session, searches, KnowledgeKind.DOC_SECTION, DOCUMENT_CANDIDATES).stream()
                .map(KnowledgeItem::ref).filter(r -> r.startsWith(DOCUMENTS_PREFIX) && r.lastIndexOf('#') > 0)
                .map(r -> r.substring(0, r.lastIndexOf('#'))).filter(r -> !designated.contains(r)).distinct().toList();
        List<ProjectKnowledge.Entry> chosen = new ArrayList<>();
        int left = budget;
        for (String ref : refs) {
            Optional<ProjectKnowledge.Entry> doc = document(session.projectId(), ref,
                    chosen.isEmpty() ? Math.min(MAX_DOCUMENT_PART, left) : MAX_DOCUMENT_PART);
            if (doc.isEmpty()) {
                continue;
            }
            int size = doc.get().title().length() + doc.get().text().length();
            if (size > left) {
                break;
            }
            chosen.add(doc.get());
            left -= size;
            if (chosen.size() == MAX_DOCUMENTS) {
                break;
            }
        }
        return chosen;
    }

    /** Un document, relu ou recomposé, coupé à une section au-delà de {@code part} (titre compris). */
    private Optional<ProjectKnowledge.Entry> document(UUID projectId, String ref, int part) {
        Optional<DocumentationReader.Page> page = reread(projectId, ref);
        String title;
        String text;
        if (page.isPresent()) {
            title = page.get().title();
            text = page.get().text();
        } else {
            List<KnowledgeItem> passages = passages(projectId, ref);
            if (passages.isEmpty()) {
                return Optional.empty();
            }
            title = documentTitle(passages.getFirst().title());
            text = String.join("\n\n", passages.stream().map(KnowledgeItem::body).toList());
        }
        if (title == null || title.isBlank()) {
            title = ref.substring(ref.lastIndexOf('/') + 1);
        }
        int room = part - title.length();
        String kept = text.length() <= room ? text : cut(text, room, DOCUMENT_TRUNCATED);
        if (kept.isBlank()) {
            return Optional.empty();
        }
        // l'empreinte suit le texte transmis : un document modifié change la connaissance
        String hash = KnowledgeItem.docSection(projectId, ref, 0, title, kept, null, null, null).contentHash();
        return Optional.of(new ProjectKnowledge.Entry(KnowledgeKind.DOC_SECTION, null, ref, 0, title, null, false,
                kept, hash));
    }

    /** Relecture dans le dossier synchronisé, si la source existe encore et le fichier se lit. */
    private Optional<DocumentationReader.Page> reread(UUID projectId, String ref) {
        if (docSources == null || folder == null || !ref.startsWith(DOCUMENTS_PREFIX)) {
            return Optional.empty();
        }
        int slash = ref.indexOf('/');
        UUID sourceId;
        try {
            sourceId = UUID.fromString(ref.substring(DOCUMENTS_PREFIX.length(), slash));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return Optional.empty();
        }
        Optional<DocSource> source = docSources.find(sourceId)
                .filter(s -> s.projectId().equals(projectId) && s.kind() == DocSource.Kind.DOCUMENTS);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        DocumentFolder.Reading reading = folder.read(source.get(), ref.substring(slash + 1));
        if (reading.failure() != null) {
            LOG.debugf("Connaissance : %s non relu (%s), passages indexés transmis", ref, reading.failure());
            return Optional.empty();
        }
        return Optional.of(reading.page());
    }

    /** Passages indexés d'un document, dans l'ordre de ses sections. */
    private List<KnowledgeItem> passages(UUID projectId, String ref) {
        // la tête d'une page d'abord, puis l'ordre d'indexation, qui est celui du document
        return index.documentParts(projectId, ref).stream()
                .sorted(Comparator.comparingInt((KnowledgeItem i) -> i.ref().equals(ref) ? 0 : 1)).toList();
    }

    /** Titre du document d'après celui d'une section (« Titre — Partie »). */
    private static String documentTitle(String sectionTitle) {
        int dash = sectionTitle.indexOf(" — ");
        return dash > 0 ? sectionTitle.substring(0, dash) : sectionTitle;
    }

    /** L'ADR entier : ses morceaux dans l'ordre, coupé à une section au-delà de {@code part} (titre compris). */
    private static ProjectKnowledge.Entry whole(KnowledgeItem adr, List<KnowledgeItem> all, int part) {
        List<KnowledgeItem> chunks = all.stream().filter(a -> a.ref().equals(adr.ref()))
                .sorted(java.util.Comparator.comparingInt(KnowledgeItem::chunk)).toList();
        KnowledgeItem first = chunks.isEmpty() ? adr : chunks.getFirst();
        String text = chunks.isEmpty() ? adr.body()
                : String.join("\n\n", chunks.stream().map(KnowledgeItem::body).toList());
        int room = part - first.title().length();
        // l'empreinte suit tous les morceaux : un changement dans l'un d'eux change la connaissance
        String hash = chunks.isEmpty() ? adr.contentHash()
                : String.join(",", chunks.stream().map(KnowledgeItem::contentHash).toList());
        return new ProjectKnowledge.Entry(first.kind(), first.role(), first.ref(), 0, first.title(),
                first.adrStatus() == null ? null : first.adrStatus().name(), false,
                text.length() <= room ? text : cut(text, room), hash);
    }

    /** Coupe à la dernière ligne de titre avant la limite, sinon au dernier paragraphe, sinon net. */
    static String cut(String text, int room) {
        return cut(text, room, ADR_TRUNCATED);
    }

    static String cut(String text, int room, String marker) {
        int limit = Math.max(0, room - marker.length());
        String head = text.substring(0, Math.min(limit, text.length()));
        int heading = head.lastIndexOf("\n#");
        int paragraph = head.lastIndexOf("\n\n");
        int end = heading > 0 ? heading : paragraph > 0 ? paragraph : head.length();
        String kept = text.substring(0, end).stripTrailing();
        return kept.isEmpty() ? "" : kept + marker;
    }

    static Optional<KnowledgeItem> inForce(KnowledgeItem adr, List<KnowledgeItem> all) {
        KnowledgeItem current = adr;
        for (int hop = 0; hop < SUCCESSION; hop++) {
            AdrStatus status = current.adrStatus();
            if (status != AdrStatus.SUPERSEDED) {
                return status == AdrStatus.DEPRECATED ? Optional.empty() : Optional.of(current);
            }
            OptionalInt number = AdrNumber.ofCited(current.supersededBy());
            if (number.isEmpty()) {
                return Optional.empty();
            }
            current = all.stream().filter(a -> AdrNumber.of(a.ref(), a.title()).equals(number)).findFirst()
                    .orElse(null);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Tickets les plus proches, hors famille de la story liée : ses sous-tâches et son parent ne sont
     * pas des travaux concurrents (FR-045). Un ticket lié explicitement à la story porte ce lien.
     */
    private List<ProjectKnowledge.Entry> tickets(Session session, StoryFamily.Family family, List<Search> searches) {
        int excluded = family.isEmpty() ? 0 : 2 + family.subtasks().size();
        List<KnowledgeItem> items = hits(session, searches, KnowledgeKind.TICKET, TICKET_CANDIDATES + excluded).stream()
                .filter(i -> !family.contains(i.ref())).limit(MAX_TICKETS).toList();
        return bounded(items, MAX_TICKET_TEXT / MAX_TICKETS).stream().map(e -> e.withLink(family.link(e.ref())))
                .toList();
    }

    /** Sous-tâches de la story liée, titres seuls : 15 au plus, 1 500 caractères en tout (research.md R29). */
    private ProjectKnowledge.Subtasks subtasks(Session session, StoryFamily.Family family) {
        if (family.isEmpty() || family.subtasks().isEmpty()) {
            return ProjectKnowledge.Subtasks.NONE;
        }
        List<ProjectKnowledge.Subtask> out = new ArrayList<>();
        int left = MAX_SUBTASK_TEXT;
        for (String key : family.subtasks()) {
            if (out.size() == MAX_SUBTASKS) {
                break;
            }
            Optional<KnowledgeItem> item = index.find(session.projectId(), new KnowledgeIndex.Key(KnowledgeKind.TICKET,
                    key, 0));
            String title = item.map(KnowledgeItem::title).orElse(key);
            if (title.length() > left) {
                title = left > 1 ? title.substring(0, left - 1) + "…" : "";
            }
            if (title.isEmpty()) {
                break;
            }
            left -= title.length();
            out.add(new ProjectKnowledge.Subtask(key, item.map(KnowledgeItem::status).orElse(null),
                    item.map(KnowledgeItem::closed).orElse(false), title));
        }
        return new ProjectKnowledge.Subtasks(family.storyKey(), out);
    }

    private static List<ProjectKnowledge.Entry> bounded(List<KnowledgeItem> items, int each) {
        return items.stream().map(i -> entry(i, Math.max(0, each - i.title().length()))).toList();
    }

    private static ProjectKnowledge.Entry entry(KnowledgeItem item, int room) {
        String body = item.body();
        String text = body.length() <= room ? body : body.substring(0, Math.max(0, room - 1)) + "…";
        String status = switch (item.kind()) {
            case ADR -> item.adrStatus() == null ? null : item.adrStatus().name();
            default -> item.status();
        };
        return new ProjectKnowledge.Entry(item.kind(), item.role(), item.ref(), item.chunk(), item.title(), status,
                item.closed(), text, item.contentHash());
    }

    /** Faits d'architecture dans la limite de 3 000 caractères : modules, puis échanges, puis constats. */
    private static ProjectKnowledge bounded(ProjectKnowledge k) {
        if (architectureSize(k) <= MAX_ARCHITECTURE) {
            return k;
        }
        int left = MAX_ARCHITECTURE;
        List<ImpactedModule> modules = new ArrayList<>();
        for (ImpactedModule m : k.modules()) {
            left -= m.name().length() + ARCHITECTURE_MARKUP;
            if (left < 0) {
                break;
            }
            modules.add(m);
        }
        List<Exchange> exchanges = new ArrayList<>();
        for (Exchange x : k.exchanges()) {
            left -= x.source().length() + x.destination().length() + length(x.via()) + ARCHITECTURE_MARKUP;
            if (left < 0) {
                break;
            }
            exchanges.add(x);
        }
        List<DeploymentFact> facts = new ArrayList<>();
        for (DeploymentFact f : k.deploymentFacts()) {
            left -= f.element().length() + length(f.other()) + ARCHITECTURE_MARKUP;
            if (left < 0) {
                break;
            }
            facts.add(f);
        }
        return k.withArchitecture(modules, exchanges, facts);
    }

    /** Taille comptée des faits d'architecture. */
    static int architectureSize(ProjectKnowledge k) {
        int size = 0;
        for (ImpactedModule m : k.modules()) {
            size += m.name().length() + ARCHITECTURE_MARKUP;
        }
        for (Exchange x : k.exchanges()) {
            size += x.source().length() + x.destination().length() + length(x.via()) + ARCHITECTURE_MARKUP;
        }
        for (DeploymentFact f : k.deploymentFacts()) {
            size += f.element().length() + length(f.other()) + ARCHITECTURE_MARKUP;
        }
        return size;
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }

    /** Besoin puis réponses, dans la limite d'une recherche. */
    static String query(String need, List<QuestionAndAnswer> answers) {
        return query(null, need, answers);
    }

    /** L'instruction d'une modification d'abord, puis le besoin et les réponses (004, FR-016). */
    static String query(String instruction, String need, List<QuestionAndAnswer> answers) {
        StringBuilder out = new StringBuilder();
        if (instruction != null && !instruction.isBlank()) {
            out.append(instruction.strip()).append('\n');
        }
        out.append(need);
        for (QuestionAndAnswer a : answers) {
            out.append('\n').append(a.answer());
        }
        return out.length() <= MAX_QUERY ? out.toString() : out.substring(0, MAX_QUERY);
    }

    private static int size(List<ProjectKnowledge.Entry> entries) {
        return entries.stream().mapToInt(e -> e.title().length() + e.text().length()).sum();
    }
}
