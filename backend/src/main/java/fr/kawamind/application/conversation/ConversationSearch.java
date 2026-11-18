package fr.kawamind.application.conversation;

import fr.kawamind.application.knowledge.ProvenanceResolver;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.knowledge.KnowledgeBudget;
import fr.kawamind.domain.knowledge.Provenance;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.knowledge.ReservedPlaces;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.port.EmbeddingPort;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.LocalSearch;
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

    /**
     * Résultat transmis à l'IA, avec sa provenance (016, {@code null} pour les modules et la
     * discussion) ; le texte n'apparaît jamais dans {@link #toString()}.
     */
    public record Found(SourceKind kind, String ref, String title, String text, Provenance provenance) {

        public Found(SourceKind kind, String ref, String title, String text) {
            this(kind, ref, title, text, null);
        }

        public boolean reference() {
            return provenance != null && provenance.level() == Reliability.REFERENCE;
        }

        @Override
        public String toString() {
            return "Found[" + kind + ", " + ref + "]";
        }
    }

    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final LocalSearch specifications;
    private final ProvenanceResolver provenance;

    public ConversationSearch(KnowledgeIndex index, EmbeddingPort embedding, LocalSearch specifications,
            ProvenanceResolver provenance) {
        this.index = index;
        this.embedding = embedding;
        this.specifications = specifications;
        this.provenance = provenance;
    }

    public ConversationSearch(KnowledgeIndex index, EmbeddingPort embedding, LocalSearch specifications) {
        this(index, embedding, specifications, ProvenanceResolver.defaults());
    }

    /** {@code kinds} vide : toutes les sortes. */
    public List<Found> run(UUID projectId, String query, Set<SourceKind> kinds) {
        Set<SourceKind> wanted = kinds == null || kinds.isEmpty() ? EnumSet.allOf(SourceKind.class) : kinds;
        record Ranked(Found found, double score) {
        }
        Map<String, Ranked> fused = new LinkedHashMap<>();
        ProvenanceResolver.Resolution resolution = provenance.forProject(projectId);
        List<KnowledgeKind> indexKinds = wanted.stream().map(ConversationSearch::indexKind)
                .filter(java.util.Objects::nonNull).toList();
        if (!indexKinds.isEmpty()) {
            float[] vector = embedding.state() == EmbeddingPort.State.READY ? embedding.query(query) : null;
            List<KnowledgeIndex.Hit> hits = index.search(projectId, query, vector,
                    vector == null ? null : embedding.modelId(), indexKinds, PER_SOURCE);
            int rank = 0;
            for (KnowledgeIndex.Hit h : hits) {
                Found f = found(h.item(), resolution.of(h.item()));
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
                        s.title(), excerpt(text), resolution.specification(s.updatedAt()));
                fused.putIfAbsent(f.kind() + "|" + f.ref(), new Ranked(f, 1.0 / (RRF_K + ++rank)));
            }
        }
        List<Ranked> ranked = new ArrayList<>(fused.values());
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed());
        // la coupe garde au moins deux extraits de référence pertinents (FR-019), les scores restent
        return ReservedPlaces.apply(ranked.stream().map(Ranked::found).toList(), MAX_RESULTS, Found::reference);
    }

    private static Found found(KnowledgeItem item, Provenance provenance) {
        String body = item.status() == null ? item.body() : "[" + item.status() + "] " + item.body();
        return new Found(sourceKind(item.kind()), item.ref(), item.title(), excerpt(body), provenance);
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
