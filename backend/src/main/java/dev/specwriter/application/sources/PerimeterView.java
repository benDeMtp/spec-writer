package dev.specwriter.application.sources;

import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.Reason;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analyse de périmètre telle que l'écran la reçoit (013, contracts/api.md) : identifiants en texte,
 * libellé de la racine de chaque module, résumé, raisons à plat.
 */
public record PerimeterView(int ordinal, List<String> terms, List<RejectedView> rejectedTerms, List<RootView> roots,
        String state, String incompleteReason, int filesScanned, int filesSkipped, List<String> skippedExamples,
        long durationMs, boolean qualified, PerimeterAnalysis.Summary summary, List<ModuleView> modules,
        List<PerimeterAnalysis.ArchLink> architectureOnly, String unavailable) {

    public record RejectedView(String term, String reason) {
    }

    public record RootView(UUID rootId, String label, String state) {
    }

    public record RefView(String id, String name) {
    }

    /** Raison à plat : seuls les champs de son {@code type} sont remplis. */
    public record ReasonView(String type, String term, Integer count, List<RefView> chain, RefView of, String element,
            String related, String description, String technology) {
    }

    /** {@code architecture} : élément du modèle correspondant ; nul si absent du modèle ou non applicable. */
    public record ModuleView(String id, String rootLabel, String path, String name, String kind, int occurrences,
            boolean isNew, String qualification, String justification, List<ReasonView> reasons, String architecture,
            String archStatus, List<Occurrence> samples) {
    }

    public static PerimeterView of(PerimeterAnalysis a) {
        Map<UUID, String> labels = new HashMap<>();
        a.roots().forEach(r -> labels.put(r.rootId(), r.label()));
        return new PerimeterView(a.ordinal(), a.terms(),
                a.rejected().stream().map((PerimeterTerms.Rejected r) -> new RejectedView(r.term(), r.reason().name()))
                        .toList(),
                a.roots().stream().map(r -> new RootView(r.rootId(), r.label(), r.state().name())).toList(),
                a.state().name(), a.incompleteReason() == null ? null : a.incompleteReason().name(), a.filesScanned(),
                a.filesSkipped(), a.skippedExamples(), a.durationMs(), a.qualified(), a.summary(),
                a.modules().stream().map(m -> module(m, labels)).toList(), a.architectureOnly(),
                a.unavailable() == null ? null : a.unavailable().name());
    }

    private static ModuleView module(PerimeterModule m, Map<UUID, String> labels) {
        return new ModuleView(m.module().id().toString(), labels.getOrDefault(m.module().rootId(), ""), m.module().path(),
                m.module().name(), m.module().kind().name(), m.occurrences(), m.isNew(), m.qualification().name(),
                m.justification(), m.reasons().stream().map(PerimeterView::reason).toList(), m.archElement(),
                m.archStatus() == null ? null : m.archStatus().name(), m.samples());
    }

    public static ReasonView reason(Reason r) {
        return switch (r) {
            case Reason.Term t -> new ReasonView("TERM", t.term(), t.count(), null, null, null, null, null, null);
            case Reason.Dependency d -> new ReasonView("DEPENDENCY", null, null,
                    d.chain().stream().map(c -> new RefView(c.id().toString(), c.name())).toList(), null, null, null, null,
                    null);
            case Reason.Aggregator g -> new ReasonView("AGGREGATOR", null, null, null,
                    new RefView(g.of().id().toString(), g.of().name()), null, null, null, null);
            case Reason.Architecture x -> new ReasonView("ARCHITECTURE", null, null, null, null, x.element(), x.related(),
                    x.description(), x.technology());
        };
    }
}
