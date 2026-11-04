package dev.specwriter.application.knowledge;

/**
 * Erreur d'une indexation, sans contenu (data-model.md, Indexation) : page ou source non lue,
 * Jira, workspace d'architecture (fichier et ligne), modèle d'embedding, ou échec. {@code WARNING}
 * signale ce qui a été ignoré sans rendre l'index partiel, comme un {@code !script}.
 */
public record IndexationError(Kind kind, String location, String reason, Integer httpStatus, String message) {

    public enum Kind {
        PAGE,
        SOURCE,
        JIRA,
        ARCHITECTURE,
        MODEL,
        FAILURE,
        WARNING
    }

    public boolean warning() {
        return kind == Kind.WARNING;
    }
}
