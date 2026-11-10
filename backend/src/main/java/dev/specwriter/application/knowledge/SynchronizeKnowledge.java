package dev.specwriter.application.knowledge;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.knowledge.IndexationProgress.Step;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.TrackedIssue;
import dev.specwriter.domain.jira.TrackedIssuePage;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSource;
import dev.specwriter.domain.port.ArchitectureReader;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.DocumentFileStates;
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.port.DocumentationReader.Section;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.KnowledgeIndex.Key;
import dev.specwriter.domain.port.KnowledgeSourceRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.TicketLinkRepository;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Synchronise l'index de connaissance d'un projet (FR-033, research.md R20) : tickets Jira,
 * sections et ADR de la documentation, modèle d'architecture, puis vecteurs. Une étape en erreur
 * n'arrête pas les suivantes ; ce qui est indexé avant une interruption est gardé. Seuls les
 * éléments dont l'empreinte change sont réécrits et revectorisés.
 */
public class SynchronizeKnowledge {

    public enum Mode { FULL, INCREMENTAL }

    static final int JIRA_PAGE = 50;
    static final int EMBEDDING_BATCH = 32;
    /** Texte transmis au modèle : au-delà, le modèle tronque de toute façon (512 jetons). */
    static final int EMBEDDING_TEXT = 2_000;

    /** Raison d'une erreur inattendue d'étape (005). */
    static final String UNEXPECTED = "UNEXPECTED";
    /** Avertissement : fichiers d'un dossier de documents ignorés pour leur format (006). */
    static final String IGNORED_FORMAT = "IGNORED_FORMAT";
    private static final String DOCUMENTS_PREFIX = "docs:";
    private static final Logger LOG = Logger.getLogger(SynchronizeKnowledge.class);

    private final ProjectRepository projects;
    private final KnowledgeSourceRepository sources;
    private final DocSourceRepository docSources;
    private final DocumentationReader reader;
    private final DocumentFolder folder;
    private final DocumentFileStates fileStates;
    private final ArchitectureReader architecture;
    private final JiraAccess jira;
    private final IssueTracker tracker;
    private final TicketLinkRepository links;
    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final ClockPort clock;

    public SynchronizeKnowledge(ProjectRepository projects, KnowledgeSourceRepository sources,
            DocSourceRepository docSources, DocumentationReader reader, DocumentFolder folder,
            DocumentFileStates fileStates, ArchitectureReader architecture, JiraAccess jira, IssueTracker tracker,
            TicketLinkRepository links, KnowledgeIndex index, EmbeddingPort embedding, ClockPort clock) {
        this.projects = projects;
        this.sources = sources;
        this.docSources = docSources;
        this.reader = reader;
        this.folder = folder;
        this.fileStates = fileStates;
        this.architecture = architecture;
        this.jira = jira;
        this.tracker = tracker;
        this.links = links;
        this.index = index;
        this.embedding = embedding;
        this.clock = clock;
    }

    public void run(UUID projectId, Mode mode, IndexationProgress progress) {
        run(projectId, mode, IndexationScope.ALL, progress);
    }

    /**
     * Sources choisies (005, US2) : seules elles sont lues ; la documentation ne retire rien et la
     * date de synchronisation ne change pas, sauf pour toutes les sources.
     */
    public void run(UUID projectId, Mode mode, IndexationScope scope, IndexationProgress progress) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        KnowledgeSource source = sources.find(projectId);
        Instant now = clock.now();

        EmbeddingPort.State model = prepareModel(progress);
        Instant mark = scope.jira() ? jira(project, source, mode, progress) : source.jiraMark();
        documentation(projectId, scope, mode, progress);
        KnowledgeSource read;
        try {
            read = scope.architecture() ? architecture(source, mode, progress) : source;
        } catch (RuntimeException | LinkageError e) {
            if (cancelling(progress, e)) {
                throw e;
            }
            unexpected(progress, IndexationError.Kind.ARCHITECTURE, source.workspace(), e);
            read = source;
        }
        // la source a pu changer pendant la synchronisation : seule l'empreinte du workspace lu s'applique
        KnowledgeSource latest = sources.find(projectId);
        KnowledgeSource kept = Objects.equals(latest.workspace(), read.workspace())
                && Objects.equals(latest.ownership(), read.ownership())
                        ? latest.withArchitectureHash(read.architectureHash())
                        : latest;
        sources.save(scope.all() ? kept.synced(mark, now) : kept.withJiraMark(mark));
        if (model == EmbeddingPort.State.READY) {
            try {
                vectors(projectId, progress);
            } catch (RuntimeException | LinkageError e) {
                if (cancelling(progress, e)) {
                    throw e;
                }
                unexpected(progress, IndexationError.Kind.MODEL, null, e);
            }
        }
    }

    /** Sources proposées au choix d'une réindexation : celles que l'indexation lirait (005, R5). */
    public List<IndexableSource> sources(UUID projectId) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        List<IndexableSource> out = new ArrayList<>();
        String projectKey = jira.configured() && jira.consented() && jira.target().isPresent()
                ? jira.effectiveProject(project)
                : null;
        if (projectKey != null) {
            out.add(new IndexableSource(IndexationScope.JIRA, IndexableSource.Kind.JIRA, "Jira " + projectKey));
        }
        docSources.list(projectId).forEach(d -> out.add(
                new IndexableSource(d.id().toString(), IndexableSource.Kind.DOCUMENTATION, d.location())));
        String workspace = sources.find(projectId).workspace();
        if (workspace != null) {
            out.add(new IndexableSource(IndexationScope.ARCHITECTURE, IndexableSource.Kind.ARCHITECTURE, workspace));
        }
        return out;
    }

    /** Sources choisies validées contre celles du projet. */
    public IndexationScope scope(UUID projectId, List<String> ids) {
        return IndexationScope.of(ids, docSources.list(projectId));
    }

    // ---------- Modèle ----------

    private EmbeddingPort.State prepareModel(IndexationProgress progress) {
        progress.step(Step.MODEL, 0);
        EmbeddingPort.State state = embedding.prepare(new EmbeddingPort.Progress() {
            @Override
            public void advanced(long done, long total) {
                progress.advance(null, (int) (done / 1_048_576), (int) (total / 1_048_576));
            }

            @Override
            public boolean cancelled() {
                return progress.cancelled();
            }
        });
        checkCancelled(progress);
        if (state != EmbeddingPort.State.READY) {
            progress.error(new IndexationError(IndexationError.Kind.MODEL, null, state.name(), null,
                    "Modèle d'embedding indisponible : recherche par les termes seuls."));
        }
        return state;
    }

    // ---------- Jira ----------

    /**
     * Tickets modifiés depuis le repère ; renvoie le nouveau repère. En mode complet avec repère, les
     * tickets fermés déjà indexés ne sont pas relus (research.md R1 de 003) : la requête ne ramène
     * que les ouverts et les modifiés, puis la liste des clés donne les absents de l'index, lus par
     * clé, et les supprimés, retirés. Sans repère, tout est relu (relecture unique, FR-008 de 003).
     */
    private Instant jira(Project project, KnowledgeSource source, Mode mode, IndexationProgress progress) {
        Instant mark = source.jiraMark();
        if (!jira.configured() || !jira.consented()) {
            return mark;
        }
        Optional<JiraAccess.Target> target = jira.target();
        String projectKey = jira.effectiveProject(project);
        if (target.isEmpty() || projectKey == null) {
            return mark;
        }
        progress.step(Step.JIRA, 0);
        if (mark == null) {
            LOG.info("Connaissance : relecture de tous les tickets (pas de repère de synchronisation)");
        }
        JiraEndpoint endpoint = target.get().endpoint();
        boolean keepClosed = mode == Mode.FULL && mark != null;
        Reading reading = new Reading(project.id(), progress, mark);
        int removed = 0;
        int keptClosed = 0;
        try {
            Instant since = mode == Mode.FULL && !keepClosed ? null : mark;
            int startAt = 0;
            while (true) {
                checkCancelled(progress);
                TrackedIssuePage page = keepClosed
                        ? tracker.searchOpenOrUpdatedSince(endpoint, projectKey, since, startAt, JIRA_PAGE)
                        : tracker.searchUpdatedSince(endpoint, projectKey, since, startAt, JIRA_PAGE);
                page.issues().forEach(issue -> reading.read(issue, page.total(), true));
                if (page.last()) {
                    break;
                }
                startAt = page.startAt() + page.issues().size();
            }
            if (mode == Mode.FULL) {
                checkCancelled(progress);
                Set<String> keys = new LinkedHashSet<>(tracker.listKeys(endpoint, projectKey));
                Set<String> closed = index.closedTickets(project.id());
                if (keepClosed) {
                    readMissing(endpoint, projectKey, keys, closed, reading);
                }
                Set<Key> keep = keys.stream().map(k -> new Key(KnowledgeKind.TICKET, k, 0)).collect(Collectors.toSet());
                removed = index.retain(project.id(), KnowledgeKind.TICKET, keep);
                links.retain(project.id(), keys);
                if (removed > 0) {
                    LOG.infof("Connaissance : %d ticket(s) supprimé(s) de Jira retiré(s) de l'index", removed);
                }
                keptClosed = (int) closed.stream().filter(keys::contains).filter(k -> !reading.keys.contains(k)).count();
            }
        } catch (IssueTrackerException e) {
            LOG.warnf("Connaissance : Jira non synchronisé (%s)", e.kind());
            progress.error(new IndexationError(IndexationError.Kind.JIRA, projectKey, e.kind().name(), null,
                    "Jira n'a pas pu être synchronisé."));
        } catch (RuntimeException | LinkageError e) {
            if (cancelling(progress, e)) {
                throw e;
            }
            unexpected(progress, IndexationError.Kind.JIRA, projectKey, e);
            // erreur inattendue : le repère reste celui d'avant l'étape (005, FR-003)
            return mark;
        } finally {
            // le bilan est transmis même en erreur ou interruption : ce qui a déjà été fait
            TicketSummary summary = new TicketSummary(reading.keys.size(), keptClosed, removed);
            progress.tickets(summary);
            LOG.infof("Connaissance : %d ticket(s) relu(s), %d fermé(s) gardé(s), %d retiré(s)", summary.read(),
                    summary.keptClosed(), summary.removed());
        }
        // le repère n'avance que jusqu'au dernier ticket lu par la requête
        return reading.mark;
    }

    /**
     * Lit par clé, par lots, les tickets absents de l'index et les tickets indexés ouverts que la
     * requête n'a pas ramenés (statut indexé faux) ; cette lecture n'avance pas le repère.
     */
    private void readMissing(JiraEndpoint endpoint, String projectKey, Set<String> keys, Set<String> closed,
            Reading reading) {
        Set<String> indexed = index.hashes(reading.projectId, KnowledgeKind.TICKET).keySet().stream().map(Key::ref)
                .collect(Collectors.toSet());
        List<String> missing = keys.stream().filter(k -> !reading.keys.contains(k))
                .filter(k -> !indexed.contains(k) || !closed.contains(k)).toList();
        for (int from = 0; from < missing.size(); from += IssueTracker.KEYS_PER_READ) {
            checkCancelled(reading.progress);
            List<String> batch = missing.subList(from, Math.min(from + IssueTracker.KEYS_PER_READ, missing.size()));
            tracker.issuesByKeys(endpoint, projectKey, batch).forEach(issue -> reading.read(issue, missing.size(), false));
        }
    }

    /** Tickets lus pendant l'étape Jira : écriture dans l'index, liens, avancement et repère. */
    private final class Reading {
        final UUID projectId;
        final IndexationProgress progress;
        final Set<String> keys = new HashSet<>();
        Instant mark;

        Reading(UUID projectId, IndexationProgress progress, Instant mark) {
            this.projectId = projectId;
            this.progress = progress;
            this.mark = mark;
        }

        void read(TrackedIssue issue, int total, boolean advancesMark) {
            index.save(ticket(projectId, issue), null, null);
            // réécrits à chaque relecture : un lien ne change pas le texte indexé (research.md R29)
            links.replace(projectId, issue.key(), issue.links());
            if (advancesMark && issue.updated() != null && (mark == null || issue.updated().isAfter(mark))) {
                mark = issue.updated();
            }
            keys.add(issue.key());
            progress.advance(issue.key(), keys.size(), total);
        }
    }

    private static KnowledgeItem ticket(UUID projectId, TrackedIssue issue) {
        String title = issue.summary() == null || issue.summary().isBlank() ? issue.key() : issue.summary().strip();
        String body = issue.description() == null ? "" : issue.description();
        return KnowledgeItem.ticket(projectId, issue.key(), truncate(title, KnowledgeItem.MAX_TITLE),
                truncate(body, KnowledgeItem.MAX_BODY), issue.status(), issue.statusCategory(), issue.issueType(),
                issue.updated(), null);
    }

    // ---------- Documentation ----------

    private void documentation(UUID projectId, IndexationScope scope, Mode mode, IndexationProgress progress) {
        List<DocSource> declared = docSources.list(projectId).stream().filter(scope::includes).toList();
        if (declared.isEmpty()) {
            return;
        }
        progress.step(Step.DOCUMENTATION, 0);
        Map<Key, String> sections = index.hashes(projectId, KnowledgeKind.DOC_SECTION);
        Map<Key, String> adrs = index.hashes(projectId, KnowledgeKind.ADR);
        Set<Key> keepSections = new HashSet<>();
        Set<Key> keepAdrs = new HashSet<>();
        boolean complete = true;
        for (DocSource source : declared) {
            checkCancelled(progress);
            try {
                if (source.kind() == DocSource.Kind.DOCUMENTS) {
                    documents(projectId, source, mode, progress);
                } else {
                    complete &= document(projectId, source, progress, sections, adrs, keepSections, keepAdrs);
                }
            } catch (RuntimeException | LinkageError e) {
                if (cancelling(progress, e)) {
                    throw e;
                }
                unexpected(progress, IndexationError.Kind.SOURCE, source.location(), e);
                complete = false;
            }
        }
        // chaque source lue en entier a déjà remplacé ses éléments (document) ; ce retrait global,
        // après une lecture complète de toutes les sources, emporte en plus les éléments sans source
        if (complete && scope.all()) {
            // les éléments des dossiers de documents sont retirés par leur source (documents)
            index.hashes(projectId, KnowledgeKind.DOC_SECTION).keySet().stream().filter(SynchronizeKnowledge::fromFolder)
                    .forEach(keepSections::add);
            index.hashes(projectId, KnowledgeKind.ADR).keySet().stream().filter(SynchronizeKnowledge::fromFolder)
                    .forEach(keepAdrs::add);
            index.retain(projectId, KnowledgeKind.DOC_SECTION, keepSections);
            index.retain(projectId, KnowledgeKind.ADR, keepAdrs);
        }
    }

    private static boolean fromFolder(Key key) {
        return key.ref().startsWith(DOCUMENTS_PREFIX);
    }

    /**
     * Dossier de documents (006, research.md R3) : seuls les documents nouveaux ou changés (taille,
     * date) sont lus, sauf en indexation complète ; un document en échec garde son indexation
     * précédente et sera relu ; les éléments des documents disparus ou exclus sont retirés, sauf
     * lecture incomplète (dossier illisible, limite atteinte).
     */
    private void documents(UUID projectId, DocSource source, Mode mode, IndexationProgress progress) {
        DocumentFolder.Scan scan = folder.scan(source);
        if (scan.problem() != null) {
            progress.error(new IndexationError(IndexationError.Kind.SOURCE, source.location(), scan.problem().name(), null,
                    null));
            return;
        }
        if (!scan.ignored().isEmpty()) {
            progress.error(new IndexationError(IndexationError.Kind.WARNING, source.location(), IGNORED_FORMAT, null,
                    scan.ignored().size() + " fichier(s) ignoré(s) : format non pris en charge"));
        }
        Map<String, DocumentFileStates.FileState> known = fileStates.of(source.id());
        Map<String, DocumentFileStates.FileState> next = new LinkedHashMap<>();
        Map<Key, String> existing = new HashMap<>(index.hashes(projectId, KnowledgeKind.DOC_SECTION));
        existing.putAll(index.hashes(projectId, KnowledgeKind.ADR));
        Set<Key> keep = new HashSet<>();
        int done = 0;
        for (DocumentFolder.FileEntry file : scan.retained()) {
            checkCancelled(progress);
            progress.advance(file.path(), done++, scan.retained().size());
            DocumentFileStates.FileState state = new DocumentFileStates.FileState(file.size(), file.modifiedAt());
            String ref = DocumentFolder.ref(source, file.path());
            if (mode == Mode.INCREMENTAL && state.equals(known.get(file.path()))) {
                keep.addAll(itemsOf(existing.keySet(), ref));
                next.put(file.path(), state);
                continue;
            }
            DocumentFolder.Reading reading = folder.read(source, file.path());
            if (reading.failure() != null) {
                progress.error(new IndexationError(IndexationError.Kind.PAGE, file.path(), reading.failure().name(), null,
                        null));
                keep.addAll(itemsOf(existing.keySet(), ref));
                continue;
            }
            for (Section section : reading.page().sections()) {
                KnowledgeItem item = item(projectId, section);
                Key key = Key.of(item);
                keep.add(key);
                if (!item.contentHash().equals(existing.get(key))) {
                    index.save(item, null, null);
                }
            }
            next.put(file.path(), state);
        }
        if (scan.limitReached()) {
            progress.error(new IndexationError(IndexationError.Kind.SOURCE, source.location(),
                    DocumentationReader.UnreadReason.LIMIT_REACHED.name(), null, null));
            Map<String, DocumentFileStates.FileState> merged = new LinkedHashMap<>(known);
            merged.putAll(next);
            fileStates.replace(source.id(), merged);
            return;
        }
        index.retainUnder(projectId, DocumentFolder.prefix(source), keep);
        fileStates.replace(source.id(), next);
    }

    /** Éléments d'un document : sa référence, ou ses sections {@code ref#n}. */
    private static List<Key> itemsOf(Set<Key> keys, String ref) {
        return keys.stream().filter(k -> k.ref().equals(ref) || k.ref().startsWith(ref + "#")).toList();
    }

    /**
     * Lit une source de documentation. Lue en entier, elle remplace ses éléments : une page retirée,
     * renommée ou renumérotée ne laisse pas de reste (010). Faux si la lecture est incomplète : ses
     * éléments précédents restent.
     */
    private boolean document(UUID projectId, DocSource source, IndexationProgress progress, Map<Key, String> sections,
            Map<Key, String> adrs, Set<Key> keepSections, Set<Key> keepAdrs) {
        boolean complete = true;
        DocumentationReader.Result read = reader.read(source, new DocumentationReader.Listener() {
            @Override
            public void reading(String location, int done, int total) {
                progress.advance(location, done, total);
            }

            @Override
            public boolean cancelled() {
                return progress.cancelled();
            }
        });
        checkCancelled(progress);
        Set<Key> readKeys = new HashSet<>();
        for (DocumentationReader.Page page : read.pages()) {
            for (Section section : page.sections()) {
                KnowledgeItem item = item(projectId, section);
                Key key = Key.of(item);
                boolean adr = item.kind() == KnowledgeKind.ADR;
                (adr ? keepAdrs : keepSections).add(key);
                readKeys.add(key);
                if (!item.contentHash().equals((adr ? adrs : sections).get(key))) {
                    index.save(item, null, null);
                }
            }
        }
        for (DocumentationReader.Unread unread : read.unread()) {
            if (unread.reason() == DocumentationReader.UnreadReason.NOT_ANTORA) {
                continue;
            }
            boolean wholeSource = read.pages().isEmpty();
            complete &= !wholeSource && unread.reason() != DocumentationReader.UnreadReason.LIMIT_REACHED;
            progress.error(new IndexationError(wholeSource ? IndexationError.Kind.SOURCE : IndexationError.Kind.PAGE,
                unread.location(), unread.reason().name(), unread.httpStatus(), null));
        }
        if (complete) {
            index.replaceSource(projectId, source.id(), readKeys);
        }
        return complete;
    }

    private KnowledgeItem item(UUID projectId, Section section) {
        String title = truncate(section.title(), KnowledgeItem.MAX_TITLE);
        String body = truncate(section.body(), KnowledgeItem.MAX_BODY);
        if (section.kind() == KnowledgeKind.ADR) {
            return KnowledgeItem.adr(projectId, section.ref(), section.chunk(), title, body, section.adr(), null, null);
        }
        return KnowledgeItem.docSection(projectId, section.ref(), section.chunk(), title, body, section.role(), null,
                null);
    }

    // ---------- Architecture ----------

    private KnowledgeSource architecture(KnowledgeSource source, Mode mode, IndexationProgress progress) {
        if (source.workspace() == null) {
            return source;
        }
        progress.step(Step.ARCHITECTURE, 0);
        checkCancelled(progress);
        ArchitectureReader.Result read = architecture.read(source.workspace(), source.ownership());
        if (read.failed()) {
            ArchitectureReader.Problem p = read.problem();
            progress.error(new IndexationError(IndexationError.Kind.ARCHITECTURE,
                    p.file() == null ? source.workspace() : p.file() + (p.line() == null ? "" : ":" + p.line()),
                    "PARSE_ERROR", null, p.message()));
            // le modèle précédent est gardé
            return source;
        }
        for (ArchitectureReader.Problem w : read.warnings()) {
            progress.error(new IndexationError(IndexationError.Kind.WARNING,
                    w.file() + (w.line() == null ? "" : ":" + w.line()), "SCRIPT_IGNORED", null, w.message()));
        }
        if (!read.warnings().isEmpty()) {
            LOG.warnf("Connaissance : %d script(s) du modèle d'architecture ignoré(s)", read.warnings().size());
        }
        if (mode == Mode.INCREMENTAL && read.hash() != null && read.hash().equals(source.architectureHash())) {
            return source;
        }
        UUID projectId = source.projectId();
        Map<Key, String> known = index.hashes(projectId, KnowledgeKind.ARCH_ELEMENT);
        Set<Key> keep = new HashSet<>();
        int done = 0;
        for (ArchElement element : read.model().elements()) {
            KnowledgeItem item = KnowledgeItem.archElement(projectId, element, null, null);
            keep.add(Key.of(item));
            index.save(item, null, null);
            progress.advance(element.name(), ++done, read.model().elements().size());
        }
        index.retain(projectId, KnowledgeKind.ARCH_ELEMENT, keep);
        index.replaceArchitecture(projectId, read.model());
        LOG.infof("Connaissance : architecture lue (%d éléments, %d relations, %d déploiements, %d nouveaux)",
                read.model().elements().size(), read.model().relations().size(), read.model().deployments().size(),
                keep.stream().filter(k -> !known.containsKey(k)).count());
        return source.withArchitectureHash(read.hash());
    }

    // ---------- Vecteurs ----------

    /** Vectorise les éléments sans vecteur du modèle courant (nouveaux, modifiés, ou autre modèle). */
    private void vectors(UUID projectId, IndexationProgress progress) {
        String model = embedding.modelId();
        int done = 0;
        boolean stepped = false;
        while (true) {
            checkCancelled(progress);
            List<KnowledgeItem> batch = index.withoutVector(projectId, model, EMBEDDING_BATCH);
            if (batch.isEmpty()) {
                return;
            }
            if (!stepped) {
                progress.step(Step.EMBEDDING, 0);
                stepped = true;
            }
            List<float[]> vectors = embedding.passages(batch.stream().map(SynchronizeKnowledge::text).toList());
            if (vectors.size() != batch.size()) {
                progress.error(new IndexationError(IndexationError.Kind.MODEL, null, "EMBEDDING_FAILED", null,
                        "Vecteurs non calculés : recherche par les termes seuls."));
                return;
            }
            for (int i = 0; i < batch.size(); i++) {
                index.saveVector(projectId, Key.of(batch.get(i)), vectors.get(i), model);
                progress.advance(batch.get(i).title(), ++done, 0);
            }
        }
    }

    static String text(KnowledgeItem item) {
        return truncate(item.title() + "\n" + item.body(), EMBEDDING_TEXT);
    }

    // ---------- Outils ----------

    /** Vrai si l'erreur vient d'une annulation : elle n'est pas une erreur d'étape (005, FR-005). */
    private static boolean cancelling(IndexationProgress progress, Throwable e) {
        return e instanceof IndexationProgress.Cancelled || progress.cancelled() || Thread.currentThread().isInterrupted();
    }

    /**
     * Erreur inattendue d'une étape (005, research.md R1) : journalisée avec sa pile, signalée dans le
     * détail sans contenu, les étapes suivantes continuent.
     */
    private static void unexpected(IndexationProgress progress, IndexationError.Kind kind, String location, Throwable e) {
        LOG.error("Connaissance : erreur inattendue à l'étape " + kind, e);
        progress.error(new IndexationError(kind, location, UNEXPECTED, null,
                "Erreur inattendue (" + e.getClass().getSimpleName() + ")"));
    }

    private static void checkCancelled(IndexationProgress progress) {
        if (progress.cancelled() || Thread.currentThread().isInterrupted()) {
            throw new IndexationProgress.Cancelled();
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
