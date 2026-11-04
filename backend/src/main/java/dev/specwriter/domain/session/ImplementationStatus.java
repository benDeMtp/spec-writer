package dev.specwriter.domain.session;

/**
 * Statut d'implémentation d'une spécification (FR-026, data-model.md de 002). L'utilisateur peut
 * tout changer ; un agent seulement READY → IN_PROGRESS et IN_PROGRESS → IMPLEMENTED (FR-027).
 */
public enum ImplementationStatus {
    DRAFT,
    READY,
    IN_PROGRESS,
    IMPLEMENTED;

    /** Qui demande le changement. */
    public enum Actor { USER, AGENT }

    /** Les features à implémenter, montrées aux agents. */
    public boolean toImplement() {
        return this == READY || this == IN_PROGRESS;
    }

    public boolean allows(ImplementationStatus target, Actor actor) {
        if (target == this) {
            return true;
        }
        if (actor == Actor.USER) {
            return true;
        }
        return this == READY && target == IN_PROGRESS || this == IN_PROGRESS && target == IMPLEMENTED;
    }
}
