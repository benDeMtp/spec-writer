package dev.specwriter.domain.specification;

import dev.specwriter.domain.project.ContextSheet;
import java.util.UUID;

/** Origine d'un use case : extrait du besoin, réponse, hypothèse ou extrait du contexte du projet (FR-011, FR-010 de 002). */
/**
 * Source d'un use case. {@code sheet} : pour une source PROJECT_CONTEXT, la fiche où figure
 * l'extrait (FR-008a) ; {@code null} sinon, ou pour une version antérieure aux deux fiches.
 */
public record SourceRef(Kind kind, UUID questionId, String excerpt, ContextSheet sheet) {

    public SourceRef(Kind kind, UUID questionId, String excerpt) {
        this(kind, questionId, excerpt, null);
    }

    public SourceRef inSheet(ContextSheet newSheet) {
        return new SourceRef(kind, questionId, excerpt, newSheet);
    }


    public enum Kind {
        NEED_EXCERPT,
        ANSWER,
        ASSUMPTION,
        PROJECT_CONTEXT
    }
}
