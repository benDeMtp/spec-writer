package fr.kawamind.domain.task;

/**
 * Suivi des tâches d'une spécification (015, R7) : suspendu quand l'utilisateur repasse la
 * spécification en Brouillon ou Prête, repris à sa demande.
 */
public enum TaskTracking {
    ACTIVE,
    SUSPENDED
}
