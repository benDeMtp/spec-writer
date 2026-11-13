package dev.specwriter.domain.sources;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Analyse de périmètre d'une réponse de l'agent (013, data-model.md) : termes, racines parcourues,
 * état, et modules trouvés. La liste des modules n'est jamais filtrée (FR-014) : la qualification
 * n'en est qu'un attribut.
 */
public record PerimeterAnalysis(int ordinal, List<String> terms, List<PerimeterTerms.Rejected> rejected,
        List<RootStatus> roots, State state, IncompleteReason incompleteReason, int filesScanned, int filesSkipped,
        List<String> skippedExamples, long durationMs, List<PerimeterModule> modules, List<ArchLink> architectureOnly,
        Unavailable unavailable) {

    public enum State { COMPLETE, INCOMPLETE }

    public enum IncompleteReason { MAX_FILES, MAX_DURATION, ROOT_MISSING, MAX_MODULES, CANCELLED }

    public enum Unavailable { NO_ROOTS }

    public enum RootState { SCANNED, MISSING, UNREADABLE }

    public record RootStatus(UUID rootId, String label, RootState state) {
    }

    /** Élément du modèle d'architecture relié à un module trouvé, sans module correspondant. */
    public record ArchLink(String element, String related, String description, String technology) {
    }

    public record Summary(int impacted, int toCheck, int notImpacted, int unqualified) {
    }

    public PerimeterAnalysis {
        terms = List.copyOf(terms);
        rejected = List.copyOf(rejected);
        roots = List.copyOf(roots);
        skippedExamples = List.copyOf(skippedExamples);
        modules = List.copyOf(modules);
        architectureOnly = List.copyOf(architectureOnly);
    }

    public static PerimeterAnalysis complete(int ordinal, List<String> terms, List<PerimeterTerms.Rejected> rejected,
            List<RootStatus> roots, int filesScanned, int filesSkipped, List<String> skippedExamples, long durationMs,
            List<PerimeterModule> modules, List<ArchLink> architectureOnly) {
        return new PerimeterAnalysis(ordinal, terms, rejected, roots, State.COMPLETE, null, filesScanned, filesSkipped,
                skippedExamples, durationMs, modules, architectureOnly, null);
    }

    /** Projet sans racine de sources : aucune analyse, la réponse invite à en déclarer (US2, scénario 5). */
    public static PerimeterAnalysis noRoots(int ordinal, List<String> terms) {
        return new PerimeterAnalysis(ordinal, terms, List.of(), List.of(), State.COMPLETE, null, 0, 0, List.of(), 0,
                List.of(), List.of(), Unavailable.NO_ROOTS);
    }

    public PerimeterAnalysis incomplete(IncompleteReason reason) {
        return new PerimeterAnalysis(ordinal, terms, rejected, roots, State.INCOMPLETE, reason, filesScanned, filesSkipped,
                skippedExamples, durationMs, modules, architectureOnly, unavailable);
    }

    public PerimeterAnalysis withModules(List<PerimeterModule> next, List<ArchLink> archOnly) {
        return new PerimeterAnalysis(ordinal, terms, rejected, roots, state, incompleteReason, filesScanned, filesSkipped,
                skippedExamples, durationMs, next, archOnly, unavailable);
    }

    public PerimeterAnalysis withOrdinal(int value) {
        return new PerimeterAnalysis(value, terms, rejected, roots, state, incompleteReason, filesScanned, filesSkipped,
                skippedExamples, durationMs, modules, architectureOnly, unavailable);
    }

    /** Trouvés par un terme, puis par dépendance, puis par l'architecture ; occurrences décroissantes ; chemin. */
    public PerimeterAnalysis sorted() {
        return withModules(modules.stream().sorted(Comparator.comparingInt(PerimeterModule::rank)
                .thenComparing(Comparator.comparingInt(PerimeterModule::occurrences).reversed())
                .thenComparing(m -> m.module().id())).toList(), architectureOnly);
    }

    /**
     * Qualifications de l'agent : un identifiant inconnu est ignoré, un module absent reste non
     * qualifié, aucun module n'est retiré.
     */
    public PerimeterAnalysis withQualifications(List<ModuleQualification> answer) {
        Map<String, ModuleQualification> byId = new HashMap<>();
        for (ModuleQualification q : answer) {
            if (q != null && q.id() != null && q.qualification() != null && q.qualification() != Qualification.UNQUALIFIED) {
                byId.putIfAbsent(q.id(), q);
            }
        }
        return withModules(modules.stream().map(m -> {
            ModuleQualification q = byId.get(m.module().id().toString());
            return q == null ? m : m.qualified(q.qualification(), q.justification());
        }).toList(), architectureOnly);
    }

    /** Vrai dès qu'un module a reçu la réponse de l'agent. */
    public boolean qualified() {
        return modules.stream().anyMatch(m -> m.justification() != null);
    }

    /** Modules absents de l'analyse précédente de la conversation (FR-015). */
    public PerimeterAnalysis markNew(PerimeterAnalysis previous) {
        if (previous == null) {
            return this;
        }
        Set<ModuleId> before = new HashSet<>();
        previous.modules().forEach(m -> before.add(m.module().id()));
        return withModules(modules.stream().map(m -> m.asNew(!before.contains(m.module().id()))).toList(),
                architectureOnly);
    }

    public Summary summary() {
        int impacted = 0;
        int toCheck = 0;
        int notImpacted = 0;
        int unqualified = 0;
        for (PerimeterModule m : modules) {
            switch (m.qualification()) {
                case IMPACTED -> impacted++;
                case TO_CHECK -> toCheck++;
                case NOT_IMPACTED -> notImpacted++;
                case UNQUALIFIED -> unqualified++;
            }
        }
        return new Summary(impacted, toCheck, notImpacted, unqualified);
    }
}
