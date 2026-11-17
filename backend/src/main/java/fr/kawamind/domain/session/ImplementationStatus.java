package fr.kawamind.domain.session;

/**
 * Statut d'implémentation d'une spécification (FR-026, data-model.md de 002). L'utilisateur peut
 * tout changer ; un agent seulement READY → IN_PROGRESS et IN_PROGRESS → IMPLEMENTED (FR-027). Quand
 * la spécification a des tâches (015, R7), le statut les suit : acteur {@link Actor#TASKS}, entre
 * READY, IN_PROGRESS et IMPLEMENTED.
 */
public enum ImplementationStatus {
    DRAFT,
    READY,
    IN_PROGRESS,
    IMPLEMENTED;

    /** Qui demande le changement. */
    public enum Actor { USER, AGENT, TASKS }

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
        if (actor == Actor.TASKS) {
            return this != DRAFT && target != DRAFT;
        }
        return this == READY && target == IN_PROGRESS || this == IN_PROGRESS && target == IMPLEMENTED;
    }
}
