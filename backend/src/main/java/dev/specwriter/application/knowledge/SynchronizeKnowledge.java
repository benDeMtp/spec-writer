package dev.specwriter.application.knowledge;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.knowledge.IndexationProgress.Step;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.TrackedIssue;
import dev.specwriter.domain.jira.TrackedIssuePage;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSource;
import dev.specwriter.domain.port.ArchitectureReader;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocSourceRepository;
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
import java.util.HashSet;
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

    private static final Logger LOG = Logger.getLogger(SynchronizeKnowledge.class);

    private final ProjectRepository projects;
    private final KnowledgeSourceRepository sources;
    private final DocSourceRepository docSources;
    private final DocumentationReader reader;
    private final ArchitectureReader architecture;
    private final JiraAccess jira;
    private final IssueTracker tracker;
    private final TicketLinkRepository links;
    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final ClockPort clock;

    public SynchronizeKnowledge(ProjectRepository projects, KnowledgeSourceRepository sources,
            DocSourceRepository docSources, DocumentationReader reader, ArchitectureReader architecture, JiraAccess jira,
            IssueTracker tracker, TicketLinkRepository links, KnowledgeIndex index, EmbeddingPort embedding,
            ClockPort clock) {
        this.projects = projects;
        this.sources = sources;
        this.docSources = docSources;
        this.reader = reader;
        this.architecture = architecture;
        this.jira = jira;
        this.tracker = tracker;
        this.links = links;
        this.index = index;
        this.embedding = embedding;
        this.clock = clock;
    }

    public void run(UUID projectId, Mode mode, IndexationProgress progress) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        KnowledgeSource source = sources.find(projectId);
        Instant now = clock.now();

        EmbeddingPort.State model = prepareModel(progress);
        Instant mark = jira(project, source, mode, progress);
        documentation(projectId, progress);
        KnowledgeSource read = architecture(source, mode, progress);
        // la source a pu changer pendant la synchronisation : seule l'empreinte du workspace lu s'applique
        KnowledgeSource latest = sources.find(projectId);
        KnowledgeSource kept = Objects.equals(latest.workspace(), read.workspace())
                && Objects.equals(latest.ownership(), read.ownership())
                        ? latest.withArchitectureHash(read.architectureHash())
                        : latest;
        sources.save(kept.synced(mark, now));
        if (model == EmbeddingPort.State.READY) {
            vectors(projectId, progress);
        }
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

    /** Tickets modifiés depuis le repère (tous en mode complet) ; renvoie le nouveau repère. */
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
        Instant since = mode == Mode.FULL ? null : mark;
        Instant newMark = mark;
        try {
            int startAt = 0;
            int done = 0;
            while (true) {
                checkCancelled(progress);
                TrackedIssuePage page = tracker.searchUpdatedSince(target.get().endpoint(), projectKey, since, startAt,
                        JIRA_PAGE);
                for (TrackedIssue issue : page.issues()) {
                    index.save(ticket(project.id(), issue), null, null);
                    // réécrits à chaque relecture : un lien ne change pas le texte indexé (research.md R29)
                    links.replace(project.id(), issue.key(), issue.links());
                    if (issue.updated() != null && (newMark == null || issue.updated().isAfter(newMark))) {
                        newMark = issue.updated();
                    }
                    progress.advance(issue.key(), ++done, page.total());
                }
                if (page.last()) {
                    break;
                }
                startAt = page.startAt() + page.issues().size();
            }
            if (mode == Mode.FULL) {
                checkCancelled(progress);
                Set<Key> keep = new HashSet<>();
                tracker.listKeys(target.get().endpoint(), projectKey)
                        .forEach(k -> keep.add(new Key(KnowledgeKind.TICKET, k, 0)));
                int removed = index.retain(project.id(), KnowledgeKind.TICKET, keep);
                links.retain(project.id(), keep.stream().map(Key::ref).collect(Collectors.toSet()));
                if (removed > 0) {
                    LOG.infof("Connaissance : %d ticket(s) supprimé(s) de Jira retiré(s) de l'index", removed);
                }
            }
            return newMark;
        } catch (IssueTrackerException e) {
            LOG.warnf("Connaissance : Jira non synchronisé (%s)", e.kind());
            progress.error(new IndexationError(IndexationError.Kind.JIRA, projectKey, e.kind().name(), null,
                    "Jira n'a pas pu être synchronisé."));
            // le repère n'avance que jusqu'au dernier ticket lu
            return newMark;
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

    private void documentation(UUID projectId, IndexationProgress progress) {
        List<DocSource> declared = docSources.list(projectId);
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
            for (DocumentationReader.Page page : read.pages()) {
                for (Section section : page.sections()) {
                    KnowledgeItem item = item(projectId, section);
                    Key key = Key.of(item);
                    boolean adr = item.kind() == KnowledgeKind.ADR;
                    (adr ? keepAdrs : keepSections).add(key);
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
        }
        // une source illisible ou une lecture bornée ne retire rien : l'index précédent reste
        if (complete) {
            index.retain(projectId, KnowledgeKind.DOC_SECTION, keepSections);
            index.retain(projectId, KnowledgeKind.ADR, keepAdrs);
        }
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
