package dev.specwriter.application.context;

import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;

/**
 * Erreur d'une génération de contexte (FR-013g) : adresse, clé ou motif, jamais un contenu de page
 * ni de ticket.
 */
public record GenerationError(Kind kind, String location, String reason, Integer httpStatus, String message) {

    public enum Kind {
        PAGE, SOURCE, JIRA, CORRECTION,
        /** Sortie du LLM corrigée par l'outil, sans nouvel appel : citations ramenées, éléments écartés. */
        REPAIRED,
        FAILURE
    }

    /** Page non lue, ou source entière quand l'emplacement est celui de la source. */
    static GenerationError of(Unread u, String sourceLocation) {
        boolean source = u.location().equals(sourceLocation) || u.reason() == UnreadReason.LIMIT_REACHED;
        return new GenerationError(source ? Kind.SOURCE : Kind.PAGE, u.location().isEmpty() ? sourceLocation : u.location(),
                u.reason().name(), u.httpStatus(), null);
    }
}
