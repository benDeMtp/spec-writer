package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.DocumentationReader.UnreadReason;

/** Document illisible, avec le motif montré à l'utilisateur ; jamais de contenu dans le message. */
final class ExtractionException extends Exception {

    private static final long serialVersionUID = 1L;

    private final UnreadReason reason;

    ExtractionException(UnreadReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    UnreadReason reason() {
        return reason;
    }
}
