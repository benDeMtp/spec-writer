package dev.specwriter.application.knowledge;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSource;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.KnowledgeSourceRepository;
import dev.specwriter.domain.port.ProjectRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** État de la connaissance d'un projet et recherche dans son index (FR-032, FR-043). */
public class KnowledgeQueries {

    public static final int MAX_QUERY = 2_000;
    public static final int MAX_HITS = 20;
    public static final int EXCERPT = 600;

    /** Compteurs, dernière synchronisation, connaissance partielle et état du modèle. */
    public record Status(long tickets, long docSections, long adrs, long architectureElements, Instant syncedAt,
            boolean partial, EmbeddingPort.State model, List<IndexableSource> sources) {
    }

    public record Hit(KnowledgeKind kind, String ref, String title, String status, ArchScope scope, String excerpt,
            String url) {
    }

    private final ProjectRepository projects;
    private final KnowledgeIndex index;
    private final KnowledgeSourceRepository sources;
    private final EmbeddingPort embedding;
    private final Indexations indexations;
    private final JiraAccess jira;

    public KnowledgeQueries(ProjectRepository projects, KnowledgeIndex index, KnowledgeSourceRepository sources,
            EmbeddingPort embedding, Indexations indexations, JiraAccess jira) {
        this.projects = projects;
        this.index = index;
        this.sources = sources;
        this.embedding = embedding;
        this.indexations = indexations;
        this.jira = jira;
    }

    public Status status(UUID projectId) {
        requireProject(projectId);
        KnowledgeSource source = sources.find(projectId);
        boolean incomplete = indexations.current(projectId)
                .map(s -> s.status() != Indexations.Status.SUCCEEDED
                        || s.errors().stream().anyMatch(e -> !e.warning())).orElse(false);
        EmbeddingPort.State model = embedding.state();
        return new Status(index.count(projectId, KnowledgeKind.TICKET), index.count(projectId, KnowledgeKind.DOC_SECTION),
                index.count(projectId, KnowledgeKind.ADR), index.count(projectId, KnowledgeKind.ARCH_ELEMENT),
                source.syncedAt(), incomplete || model != EmbeddingPort.State.READY, model,
                indexations.sources(projectId));
    }

    /** Recherche hybride, ou par les termes seuls si le modèle n'est pas prêt (FR-043). */
    public List<Hit> search(UUID projectId, String query, Collection<KnowledgeKind> kinds) {
        requireProject(projectId);
        if (query == null || query.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Saisissez des mots à rechercher.");
        }
        if (query.length() > MAX_QUERY) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La recherche dépasse 2 000 caractères.");
        }
        float[] vector = embedding.state() == EmbeddingPort.State.READY ? embedding.query(query) : null;
        return index.search(projectId, query, vector, vector == null ? null : embedding.modelId(), kinds, MAX_HITS)
                .stream().map(h -> hit(h.item())).toList();
    }

    private Hit hit(KnowledgeItem item) {
        String body = item.body();
        String excerpt = body.length() <= EXCERPT ? body : body.substring(0, EXCERPT - 1) + "…";
        return new Hit(item.kind(), item.ref(), item.title(), item.status(), item.scope(), excerpt, url(item));
    }

    private String url(KnowledgeItem item) {
        return switch (item.kind()) {
            case TICKET -> jira.target().map(t -> t.issueUrl(item.ref())).orElse(null);
            case DOC_SECTION, ADR -> item.ref().toLowerCase(Locale.ROOT).startsWith("http") ? item.ref() : null;
            case ARCH_ELEMENT -> null;
        };
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
