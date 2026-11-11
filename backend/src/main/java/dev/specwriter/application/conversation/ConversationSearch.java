package dev.specwriter.application.conversation;

import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.LocalSearch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Une recherche de l'agent (010, research.md R3) : index de la connaissance du projet et dernière
 * version de ses spécifications, fusionnés par rang (RRF), {@link #MAX_RESULTS} au plus, extraits
 * bornés. Le projet courant seul (FR-026).
 */
public class ConversationSearch {

    public static final int MAX_RESULTS = 8;
    /** Préfixe de la référence d'une spécification citée : {@code spec:<session>@v<n>}. */
    public static final String SPEC_PREFIX = "spec:";
    private static final int RRF_K = 60;
    private static final int PER_SOURCE = 20;

    /** Résultat transmis à l'IA ; le texte n'apparaît jamais dans {@link #toString()}. */
    public record Found(SourceKind kind, String ref, String title, String text) {

        @Override
        public String toString() {
            return "Found[" + kind + ", " + ref + "]";
        }
    }

    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final LocalSearch specifications;

    public ConversationSearch(KnowledgeIndex index, EmbeddingPort embedding, LocalSearch specifications) {
        this.index = index;
        this.embedding = embedding;
        this.specifications = specifications;
    }

    /** {@code kinds} vide : toutes les sortes. */
    public List<Found> run(UUID projectId, String query, Set<SourceKind> kinds) {
        Set<SourceKind> wanted = kinds == null || kinds.isEmpty() ? EnumSet.allOf(SourceKind.class) : kinds;
        record Ranked(Found found, double score) {
        }
        Map<String, Ranked> fused = new LinkedHashMap<>();
        List<KnowledgeKind> indexKinds = wanted.stream().map(ConversationSearch::indexKind)
                .filter(java.util.Objects::nonNull).toList();
        if (!indexKinds.isEmpty()) {
            float[] vector = embedding.state() == EmbeddingPort.State.READY ? embedding.query(query) : null;
            List<KnowledgeIndex.Hit> hits = index.search(projectId, query, vector,
                    vector == null ? null : embedding.modelId(), indexKinds, PER_SOURCE);
            int rank = 0;
            for (KnowledgeIndex.Hit h : hits) {
                Found f = found(h.item());
                double score = 1.0 / (RRF_K + ++rank);
                fused.merge(f.kind() + "|" + f.ref(), new Ranked(f, score),
                        (a, b) -> new Ranked(a.found(), Math.max(a.score(), b.score())));
            }
        }
        if (wanted.contains(SourceKind.SPECIFICATION)) {
            int rank = 0;
            for (LocalSearch.SpecHit s : specifications.byRelevance(projectId, query, PER_SOURCE)) {
                String text = (s.implemented() ? "(feature implémentée) " : "") + s.text();
                Found f = new Found(SourceKind.SPECIFICATION, SPEC_PREFIX + s.sessionId() + "@v" + s.version(),
                        s.title(), excerpt(text));
                fused.putIfAbsent(f.kind() + "|" + f.ref(), new Ranked(f, 1.0 / (RRF_K + ++rank)));
            }
        }
        List<Ranked> ranked = new ArrayList<>(fused.values());
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed());
        return ranked.stream().limit(MAX_RESULTS).map(Ranked::found).toList();
    }

    private static Found found(KnowledgeItem item) {
        String body = item.status() == null ? item.body() : "[" + item.status() + "] " + item.body();
        return new Found(sourceKind(item.kind()), item.ref(), item.title(), excerpt(body));
    }

    static String excerpt(String text) {
        String t = text == null ? "" : text.strip();
        int max = KnowledgeBudget.SOURCE_EXCERPT;
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static KnowledgeKind indexKind(SourceKind kind) {
        return switch (kind) {
            case TICKET -> KnowledgeKind.TICKET;
            case DOC_SECTION -> KnowledgeKind.DOC_SECTION;
            case ADR -> KnowledgeKind.ADR;
            case ARCH_ELEMENT -> KnowledgeKind.ARCH_ELEMENT;
            case SPECIFICATION, ATTACHMENT, DISCUSSION, MODULE -> null;
        };
    }

    private static SourceKind sourceKind(KnowledgeKind kind) {
        return switch (kind) {
            case TICKET -> SourceKind.TICKET;
            case DOC_SECTION -> SourceKind.DOC_SECTION;
            case ADR -> SourceKind.ADR;
            case ARCH_ELEMENT -> SourceKind.ARCH_ELEMENT;
        };
    }
}
