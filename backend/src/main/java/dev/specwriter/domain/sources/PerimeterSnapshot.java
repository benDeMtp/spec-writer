package dev.specwriter.domain.sources;

import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.specification.Assumption;
import dev.specwriter.domain.specification.SpecificationContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Périmètre repris dans une spécification (013, US5, research.md R11) : la dernière analyse de la
 * conversation, copiée dans la session quand la spécification en est rédigée. Il garde au plus
 * {@value #MAX_PROOFS} occurrences par module, réduites à {@code fichier:ligne} : aucun extrait de
 * code n'entre dans la spécification.
 */
public record PerimeterSnapshot(List<Entry> modules, boolean incomplete) {

    public static final int MAX_PROOFS = 3;

    /** Module repris : identité lisible, qualification, justification, raisons et preuves. */
    public record Entry(String name, String path, String rootLabel, Qualification qualification, String justification,
            List<Reason> reasons, List<Occurrence> occurrences) {

        public Entry {
            reasons = List.copyOf(reasons);
            occurrences = List.copyOf(occurrences);
        }

        /** Un module « à vérifier » ou laissé sans qualification reste une hypothèse à valider. */
        public boolean toCheck() {
            return qualification == Qualification.TO_CHECK || qualification == Qualification.UNQUALIFIED;
        }
    }

    public PerimeterSnapshot {
        modules = List.copyOf(modules);
    }

    public static PerimeterSnapshot of(PerimeterAnalysis analysis) {
        Map<UUID, String> labels = new HashMap<>();
        analysis.roots().forEach(r -> labels.put(r.rootId(), r.label()));
        List<Entry> entries = new ArrayList<>();
        for (PerimeterModule m : analysis.modules()) {
            List<Occurrence> proofs = m.samples().stream().limit(MAX_PROOFS)
                    .map(o -> new Occurrence(o.path(), o.line(), o.term(), "")).toList();
            entries.add(new Entry(m.module().name(), m.module().path(), labels.getOrDefault(m.module().rootId(), ""),
                    m.qualification(), m.justification(), m.reasons(), proofs));
        }
        return new PerimeterSnapshot(entries, analysis.state() == PerimeterAnalysis.State.INCOMPLETE);
    }

    /**
     * Hypothèses de l'outil pour les modules à vérifier, numérotées après celles de la réponse
     * (FR-021) ; une hypothèse déjà présente, à l'énoncé près, n'est pas répétée (révision).
     */
    public SpecificationContent withAssumptions(SpecificationContent content, Language language) {
        Set<String> known = new HashSet<>();
        content.assumptions().forEach(a -> known.add(a.statement()));
        List<Assumption> all = new ArrayList<>(content.assumptions());
        for (Entry e : modules) {
            String statement = statement(e, language);
            if (e.toCheck() && known.add(statement)) {
                all.add(new Assumption("H-%02d".formatted(all.size() + 1), statement, Assumption.Origin.TOOL_INFERENCE,
                        null));
            }
        }
        return all.size() == content.assumptions().size() ? content
                : new SpecificationContent(content.title(), content.summary(), content.details(), content.useCases(),
                        content.scenarios(), all, content.requirements(), content.adrDeviations());
    }

    static String statement(Entry e, Language language) {
        String where = e.path().isEmpty() ? e.rootLabel() : e.path();
        String reason = e.reasons().isEmpty() ? null : reason(e.reasons().getFirst(), language);
        if (language == Language.EN) {
            return "Module " + e.name() + " (" + where + ") is to be checked" + (reason == null ? "." : ": " + reason + ".");
        }
        return "Le module " + e.name() + " (" + where + ") est à vérifier" + (reason == null ? "." : " : " + reason + ".");
    }

    /** Raison lisible d'un module, dans la langue de la spécification. */
    public static String reason(Reason reason, Language language) {
        boolean en = language == Language.EN;
        return switch (reason) {
            case Reason.Term t -> (en ? "contains " : "contient ") + t.term() + " ×" + t.count();
            case Reason.Dependency d -> (en ? "depends on " : "dépend de ")
                    + d.chain().stream().map(ModuleRef::name).collect(Collectors.joining(" → "));
            case Reason.Aggregator g -> (en ? "aggregates " : "agrège ") + g.of().name();
            case Reason.Architecture x -> (en ? "linked to " : "relié à ") + x.related()
                    + (x.technology() == null ? "" : " (" + x.technology() + ")");
        };
    }
}
