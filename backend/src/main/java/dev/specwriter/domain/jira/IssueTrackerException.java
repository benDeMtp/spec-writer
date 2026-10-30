package dev.specwriter.domain.jira;

import dev.specwriter.domain.error.ErrorCode;

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

    /** Code d'erreur présenté à l'utilisateur. */
    public ErrorCode errorCode() {
        return switch (kind) {
            case UNAUTHORIZED -> ErrorCode.JIRA_TOKEN_REJECTED;
            case FORBIDDEN -> ErrorCode.JIRA_PERMISSION_DENIED;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case REJECTED -> ErrorCode.JIRA_REJECTED;
            case UNREACHABLE, RATE_LIMITED -> ErrorCode.JIRA_UNREACHABLE;
        };
    }
}
