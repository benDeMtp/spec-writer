package dev.specwriter.domain.question;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;

/** Réponse à une question ; {@code assumption} quand la question a été passée (FR-006). */
public record Answer(UUID questionId, String value, boolean assumption, Instant answeredAt) {

    public static final int MAX_LENGTH = 5_000;

    public Answer {
        value = value == null ? "" : value.strip();
        if (!assumption && value.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Saisissez une réponse, ou passez la question.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La réponse dépasse 5 000 caractères.");
        }
    }
}
