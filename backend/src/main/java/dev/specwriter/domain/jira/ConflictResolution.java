package dev.specwriter.domain.jira;

/** Choix de l'utilisateur après un conflit (FR-048). */
public enum ConflictResolution {
    OVERWRITE,
    TAKE_JIRA,
    AI_MERGE
}
