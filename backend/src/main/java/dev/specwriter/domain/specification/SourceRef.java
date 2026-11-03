package dev.specwriter.domain.specification;

import java.util.UUID;

/** Origine d'un use case : extrait du besoin, réponse, hypothèse ou extrait du contexte du projet (FR-011, FR-010 de 002). */
public record SourceRef(Kind kind, UUID questionId, String excerpt) {

    public enum Kind {
        NEED_EXCERPT,
        ANSWER,
        ASSUMPTION,
        PROJECT_CONTEXT
    }
}
