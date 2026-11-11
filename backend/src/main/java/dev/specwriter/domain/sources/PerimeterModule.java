package dev.specwriter.domain.sources;

import java.util.List;

/**
 * Module d'une analyse de périmètre (013, data-model.md) : ses raisons, le nombre exact
 * d'occurrences, un échantillon, sa qualification et la justification de l'agent.
 */
public record PerimeterModule(Module module, List<Reason> reasons, int occurrences, List<Occurrence> samples,
        Qualification qualification, String justification, boolean isNew, String archElement, ArchStatus archStatus) {

    public static final int MAX_JUSTIFICATION = 300;
    public static final int MAX_SAMPLES_PER_TERM = 20;

    /** Correspondance du module dans le modèle d'architecture (US4). */
    public enum ArchStatus { MATCHED, ABSENT }

    public PerimeterModule {
        reasons = List.copyOf(reasons);
        samples = List.copyOf(samples);
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("Un module du périmètre a au moins une raison");
        }
        if (justification != null && justification.length() > MAX_JUSTIFICATION) {
            justification = justification.substring(0, MAX_JUSTIFICATION);
        }
    }

    /**
     * Module trouvé, pas encore qualifié : trouvé par un terme, il attend l'agent ; trouvé seulement
     * par propagation, il est à vérifier.
     */
    public static PerimeterModule found(Module module, List<Reason> reasons, int occurrences, List<Occurrence> samples) {
        boolean direct = reasons.stream().anyMatch(Reason.Term.class::isInstance);
        return new PerimeterModule(module, reasons, occurrences, samples,
                direct ? Qualification.UNQUALIFIED : Qualification.TO_CHECK, null, false, null, null);
    }

    public boolean direct() {
        return reasons.stream().anyMatch(Reason.Term.class::isInstance);
    }

    /** Rang du tri : trouvé par un terme, puis par dépendance, puis par l'architecture. */
    int rank() {
        if (direct()) {
            return 0;
        }
        return reasons.stream().anyMatch(r -> r instanceof Reason.Dependency || r instanceof Reason.Aggregator) ? 1 : 2;
    }

    /** Qualification de l'agent ; « impacté » exige une occurrence, sinon le module reste à vérifier. */
    public PerimeterModule qualified(Qualification q, String why) {
        Qualification kept = q == Qualification.IMPACTED && !direct() ? Qualification.TO_CHECK : q;
        return new PerimeterModule(module, reasons, occurrences, samples, kept, why, isNew, archElement, archStatus);
    }

    public PerimeterModule withReasons(List<Reason> more) {
        List<Reason> all = new java.util.ArrayList<>(reasons);
        all.addAll(more);
        return new PerimeterModule(module, all, occurrences, samples, qualification, justification, isNew, archElement,
                archStatus);
    }

    public PerimeterModule asNew(boolean value) {
        return new PerimeterModule(module, reasons, occurrences, samples, qualification, justification, value, archElement,
                archStatus);
    }

    public PerimeterModule inArchitecture(String element, ArchStatus status) {
        return new PerimeterModule(module, reasons, occurrences, samples, qualification, justification, isNew, element,
                status);
    }
}
