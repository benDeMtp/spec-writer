package fr.kawamind.domain.specification;

import java.util.UUID;

/**
 * Hypothèse explicite (FR-013) : question passée ou déduction de l'outil. Rattachée à un élément
 * (021, {@code anchor}) ; {@code fromCode} : son code dans la version de base, posé par une révision.
 */
public record Assumption(String code, String statement, Origin origin, UUID questionId, Anchor anchor, String fromCode) {

    /** Sans rattachement (versions antérieures à 021). */
    public Assumption(String code, String statement, Origin origin, UUID questionId) {
        this(code, statement, origin, questionId, null, null);
    }

    public Assumption {
        fromCode = fromCode == null || fromCode.isBlank() ? null : fromCode.strip();
    }

    public Assumption withCode(String newCode) {
        return new Assumption(newCode, statement, origin, questionId, anchor, fromCode);
    }

    public Assumption withAnchor(Anchor newAnchor) {
        return new Assumption(code, statement, origin, questionId, newAnchor, fromCode);
    }

    public enum Origin {
        SKIPPED_QUESTION,
        TOOL_INFERENCE
    }
}
