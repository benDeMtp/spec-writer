package dev.specwriter.domain.specification;

import java.util.UUID;

/** Origine d'un use case : extrait du besoin, réponse ou hypothèse (FR-011). */
public record SourceRef(Kind kind, UUID questionId, String excerpt) {

    public enum Kind {
        NEED_EXCERPT,
        ANSWER,
        ASSUMPTION
    }
}
