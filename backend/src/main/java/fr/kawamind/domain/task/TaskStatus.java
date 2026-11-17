package fr.kawamind.domain.task;

/**
 * Statut d'une tâche (015, data-model.md). {@code MISSING} : la sous-tâche n'est plus trouvée sous
 * sa clé dans Jira ; la tâche sort de l'avancement et ne peut plus être prise.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    MISSING
}
