package dev.specwriter.domain.specification;

import java.util.UUID;

/** Hypothèse explicite (FR-013) : question passée ou déduction de l'outil. */
public record Assumption(String code, String statement, Origin origin, UUID questionId) {

    public enum Origin {
        SKIPPED_QUESTION,
        TOOL_INFERENCE
    }
}
