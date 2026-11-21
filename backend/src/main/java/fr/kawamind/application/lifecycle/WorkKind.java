package fr.kawamind.application.lifecycle;

/** Nature d'un travail en cours, nommé dans la confirmation de Quitter (019, contracts/rest-api.md). */
public enum WorkKind {
    /** Indexation ou synchronisation automatique des sources. */
    INDEXATION,
    /** Réponse de l'agent dans une conversation. */
    AGENT_REPLY,
    /** Proposition d'une nouvelle version dans une discussion. */
    REVISION,
    /** Génération d'une spécification ou d'une version, synchrone. */
    GENERATION
}
