package dev.specwriter.domain.agent;

/** Opérations d'un agent consignées dans le journal (FR-028) ; la recherche ne l'est pas (R26). */
public enum AgentOperation {
    LIST_PROJECTS,
    LIST_FEATURES,
    GET_FEATURE,
    SET_STATUS,
    SEARCH_KNOWLEDGE
}
