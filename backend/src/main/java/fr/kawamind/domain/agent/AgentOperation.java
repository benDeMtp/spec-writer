package fr.kawamind.domain.agent;

/**
 * Opérations d'un agent consignées dans le journal (FR-028) ; la recherche ne l'est pas (R26). Les
 * opérations de tâches (015, R11) portent la clé Jira de la tâche ; {@code RELEASE_TASK} est une
 * libération par l'utilisateur d'une tâche tenue par l'agent nommé.
 */
public enum AgentOperation {
    LIST_PROJECTS,
    LIST_FEATURES,
    GET_FEATURE,
    SET_STATUS,
    SEARCH_KNOWLEDGE,
    NEXT_TASK,
    START_TASK,
    COMPLETE_TASK,
    RELEASE_TASK
}
