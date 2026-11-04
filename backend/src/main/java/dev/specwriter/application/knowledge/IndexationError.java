package dev.specwriter.application.knowledge;

/**
 * Erreur d'une indexation, sans contenu (data-model.md, Indexation) : page ou source non lue,
 * Jira, workspace d'architecture (fichier et ligne), modèle d'embedding, ou échec.
 */
public record IndexationError(Kind kind, String location, String reason, Integer httpStatus, String message) {

    public enum Kind {
        PAGE,
        SOURCE,
        JIRA,
        ARCHITECTURE,
        MODEL,
        FAILURE
    }
}
