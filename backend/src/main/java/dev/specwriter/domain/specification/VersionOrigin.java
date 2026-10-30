package dev.specwriter.domain.specification;

/** Origine d'une version (data-model.md, SpecificationVersion). */
public enum VersionOrigin {
    GENERATED,
    AI_EDIT,
    JIRA_IMPORT,
    CONFLICT_MERGE
}
