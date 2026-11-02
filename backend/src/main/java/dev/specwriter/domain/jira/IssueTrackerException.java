package dev.specwriter.domain.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.List;

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
    /** Causes données par Jira (par exemple les champs refusés), présentables à l'utilisateur. */
    private final List<String> details;

    public IssueTrackerException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, List.of());
    }

    public IssueTrackerException(Kind kind, String message, Throwable cause, List<String> details) {
        super(message, cause);
        this.kind = kind;
        this.details = List.copyOf(details);
    }

    public IssueTrackerException(Kind kind, String message) {
        this(kind, message, null);
    }

    public Kind kind() {
        return kind;
    }

    public List<String> details() {
        return details;
    }

    /** Erreur présentée à l'utilisateur : le message du code, suivi des causes données par Jira. */
    public DomainException toDomain() {
        ErrorCode code = errorCode();
        String message = details.isEmpty() ? code.defaultMessage()
                : code.defaultMessage() + " " + String.join(" ; ", details) + ".";
        return new DomainException(code, message, this);
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
