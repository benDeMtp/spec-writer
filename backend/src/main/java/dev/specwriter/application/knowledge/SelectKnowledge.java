package dev.specwriter.application.knowledge;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.knowledge.AdrNumber;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSelection.ImpactedModule;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.session.Session;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Sélection de la connaissance d'une session (research.md R24) : la fiche générale toujours ; le
 * vocabulaire et la vue d'ensemble (3 000 caractères) ; 4 ADR au plus (6 000), un ADR remplacé
 * l'étant par son successeur et un ADR obsolète écarté ; 3 tickets (3 000) ; pour une
 * spécification, les faits d'architecture (3 000). Le tout tient en 35 000 caractères. La recherche
 * part du besoin et des réponses : la sélection suit leurs changements.
 */
public class SelectKnowledge implements SessionKnowledge {

    static final int MAX_ROLES = 3_000;
    static final int MAX_ADRS = 4;
    static final int MAX_ADR_TEXT = 6_000;
    /** Au plus par ADR : au-delà, l'ADR est coupé à la fin d'une section (004, research.md R4). */
    static final int MAX_ADR_PART = 4_000;
    static final String ADR_TRUNCATED = "\n\n[ADR tronqué]";
    static final int MAX_TICKETS = 3;
    static final int MAX_TICKET_TEXT = 3_000;
    static final int MAX_ARCHITECTURE = 3_000;
    static final int MAX_SUBTASKS = 15;
    static final int MAX_SUBTASK_TEXT = 1_500;
    static final int MAX_QUERY = KnowledgeQueries.MAX_QUERY;
    /** Taille comptée pour un module, un échange ou un constat, en plus de ses noms. */
    private static final int ARCHITECTURE_MARKUP = 40;
    private static final int ADR_CANDIDATES = 12;
    private static final int SUCCESSION = 5;

    private static final Logger LOG = Logger.getLogger(SelectKnowledge.class);

    private final ProjectRepository projects;
    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final AnalyzeArchitecture analyze;
    private final StoryFamily families;

    public SelectKnowledge(ProjectRepository projects, KnowledgeIndex index, EmbeddingPort embedding,
            AnalyzeArchitecture analyze, StoryFamily families) {
        this.projects = projects;
        this.index = index;
        this.embedding = embedding;
        this.analyze = analyze;
        this.families = families;
    }

    public SelectKnowledge(ProjectRepository projects, KnowledgeIndex index, EmbeddingPort embedding,
            AnalyzeArchitecture analyze) {
        this(projects, index, embedding, analyze, StoryFamily.none());
    }

    @Override
    public ProjectKnowledge forQuestions(Session session, List<QuestionAndAnswer> answers) {
        return select(session, answers, false);
    }

    @Override
    public ProjectKnowledge forSpecification(Session session, List<QuestionAndAnswer> answers) {
        return select(session, answers, true);
    }

    private ProjectKnowledge select(Session session, List<QuestionAndAnswer> answers, boolean architecture) {
        ProjectKnowledge sheet = SessionKnowledge.sheet(projects, session);
        String query = query(session.need().text(), answers);
        float[] vector = null;
        if (embedding.state() == EmbeddingPort.State.READY) {
            try {
                vector = embedding.query(query);
            } catch (RuntimeException e) {
                // la recherche se rabat sur les termes (FR-043)
                LOG.warnf("Connaissance : vecteur de la recherche indisponible (%s)", e.getClass().getSimpleName());
            }
        }
        String model = vector == null ? null : embedding.modelId();
        StoryFamily.Family family = families.of(session);
        List<ProjectKnowledge.Entry> entries = new ArrayList<>();
        entries.addAll(roles(session));
        entries.addAll(adrs(session, query, vector, model));
        entries.addAll(tickets(session, family, query, vector, model));
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

    /** Vocabulaire et vue d'ensemble, toujours transmis (FR-042), dans la limite de 3 000 caractères. */
    private List<ProjectKnowledge.Entry> roles(Session session) {
        List<ProjectKnowledge.Entry> out = new ArrayList<>();
        int left = MAX_ROLES;
        for (KnowledgeItem item : index.withRole(session.projectId())) {
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
     * ADR en vigueur les plus proches ; un remplacé cède la place à son successeur, un obsolète est
     * écarté (FR-036). Chaque ADR est transmis entier, morceaux dans l'ordre (004, research.md R4) :
     * {@link #MAX_ADR_PART} au plus par ADR, {@link #MAX_ADR_TEXT} au total ; le premier qui ne tient
     * plus arrête la liste.
     */
    private List<ProjectKnowledge.Entry> adrs(Session session, String query, float[] vector, String model) {
        List<KnowledgeIndex.Hit> hits = index.search(session.projectId(), query, vector, model, List.of(KnowledgeKind.ADR),
                ADR_CANDIDATES);
        if (hits.isEmpty()) {
            return List.of();
        }
        List<KnowledgeItem> all = index.all(session.projectId(), KnowledgeKind.ADR);
        Set<String> seen = new LinkedHashSet<>();
        List<ProjectKnowledge.Entry> chosen = new ArrayList<>();
        int left = MAX_ADR_TEXT;
        for (KnowledgeIndex.Hit hit : hits) {
            Optional<KnowledgeItem> current = inForce(hit.item(), all);
            if (current.isEmpty() || !seen.add(current.get().ref())) {
                continue;
            }
            ProjectKnowledge.Entry entry = whole(current.get(), all);
            int size = entry.title().length() + entry.text().length();
            if (size > left) {
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

    /** L'ADR entier : ses morceaux dans l'ordre, coupé à une section au-delà de {@link #MAX_ADR_PART}. */
    private static ProjectKnowledge.Entry whole(KnowledgeItem adr, List<KnowledgeItem> all) {
        List<KnowledgeItem> chunks = all.stream().filter(a -> a.ref().equals(adr.ref()))
                .sorted(java.util.Comparator.comparingInt(KnowledgeItem::chunk)).toList();
        KnowledgeItem first = chunks.isEmpty() ? adr : chunks.getFirst();
        String text = chunks.isEmpty() ? adr.body()
                : String.join("\n\n", chunks.stream().map(KnowledgeItem::body).toList());
        int room = MAX_ADR_PART - first.title().length();
        // l'empreinte suit tous les morceaux : un changement dans l'un d'eux change la connaissance
        String hash = chunks.isEmpty() ? adr.contentHash()
                : String.join(",", chunks.stream().map(KnowledgeItem::contentHash).toList());
        return new ProjectKnowledge.Entry(first.kind(), first.role(), first.ref(), 0, first.title(),
                first.adrStatus() == null ? null : first.adrStatus().name(), false,
                text.length() <= room ? text : cut(text, room), hash);
    }

    /** Coupe à la dernière ligne de titre avant la limite, sinon au dernier paragraphe, sinon net. */
    static String cut(String text, int room) {
        int limit = Math.max(0, room - ADR_TRUNCATED.length());
        String head = text.substring(0, Math.min(limit, text.length()));
        int heading = head.lastIndexOf("\n#");
        int paragraph = head.lastIndexOf("\n\n");
        int end = heading > 0 ? heading : paragraph > 0 ? paragraph : head.length();
        return text.substring(0, end).stripTrailing() + ADR_TRUNCATED;
    }

    private static Optional<KnowledgeItem> inForce(KnowledgeItem adr, List<KnowledgeItem> all) {
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
    private List<ProjectKnowledge.Entry> tickets(Session session, StoryFamily.Family family, String query,
            float[] vector, String model) {
        int excluded = family.isEmpty() ? 0 : 2 + family.subtasks().size();
        List<KnowledgeItem> items = index.search(session.projectId(), query, vector, model, List.of(KnowledgeKind.TICKET),
                MAX_TICKETS + excluded).stream().map(KnowledgeIndex.Hit::item).filter(i -> !family.contains(i.ref()))
                .limit(MAX_TICKETS).toList();
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
        StringBuilder out = new StringBuilder(need);
        for (QuestionAndAnswer a : answers) {
            out.append('\n').append(a.answer());
        }
        return out.length() <= MAX_QUERY ? out.toString() : out.substring(0, MAX_QUERY);
    }
}
