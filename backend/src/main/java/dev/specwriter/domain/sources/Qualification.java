package dev.specwriter.domain.sources;

/** Qualification d'un module du périmètre par l'agent (013, FR-013). */
public enum Qualification {
    IMPACTED,
    TO_CHECK,
    NOT_IMPACTED,
    /** L'agent ne s'est pas prononcé : le module reste affiché. */
    UNQUALIFIED
}
