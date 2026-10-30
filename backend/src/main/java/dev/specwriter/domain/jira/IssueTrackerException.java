package dev.specwriter.domain.jira;

/** Échec d'un appel au gestionnaire de tickets, classé pour l'utilisateur. Sans contenu de réponse. */
public class IssueTrackerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        UNREACHABLE,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        RATE_LIMITED,
        REJECTED
    }

    private final Kind kind;

    public IssueTrackerException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public IssueTrackerException(Kind kind, String message) {
        this(kind, message, null);
    }

    public Kind kind() {
        return kind;
    }
}
