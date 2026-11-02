package dev.specwriter.domain.error;

import java.util.Objects;

/**
 * Erreur métier présentable à l'utilisateur. Le message ne doit jamais contenir de contenu saisi ni
 * de jeton (FR-025, FR-034).
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final String userMessage;

    public DomainException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public DomainException(ErrorCode code, String userMessage) {
        super(code.name());
        this.code = Objects.requireNonNull(code);
        this.userMessage = Objects.requireNonNull(userMessage);
    }

    public DomainException(ErrorCode code, String userMessage, Throwable cause) {
        super(code.name(), cause);
        this.code = Objects.requireNonNull(code);
        this.userMessage = Objects.requireNonNull(userMessage);
    }

    public ErrorCode code() {
        return code;
    }

    public String userMessage() {
        return userMessage;
    }
}
