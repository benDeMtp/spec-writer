package dev.specwriter.domain.specification;

/** Origine d'une version (data-model.md, SpecificationVersion). */
public enum VersionOrigin {
    GENERATED,
    AI_EDIT,
    JIRA_IMPORT,
    CONFLICT_MERGE,
    /** Proposition rédigée d'après la discussion de la spécification (011). */
    DISCUSSION
}
