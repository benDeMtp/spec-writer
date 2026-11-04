package dev.specwriter.domain.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Question de clarification sur une fiche, répondue ou passée, numérotée dans le projet (FR-013h,
 * FR-013i). Une réponse est une source citable des fiches, sous la référence {@code Q-n}.
 */
public record ContextQuestion(UUID id, UUID projectId, int number, ContextSheet sheet, String label, String rationale,
        String answer, State state, Instant askedAt, Instant answeredAt) {

    public enum State { ANSWERED, SKIPPED }

    public static final int MAX_LABEL = 500;
    public static final int MAX_ANSWER = 2_000;

    public ContextQuestion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(sheet);
        Objects.requireNonNull(state);
        label = bounded(label, MAX_LABEL);
        rationale = bounded(rationale, MAX_LABEL);
        answer = state == State.SKIPPED ? "" : validAnswer(answer);
    }

    /** Réponse à une question posée, ou question passée si {@code skip}. */
    public static ContextQuestion recorded(UUID id, UUID projectId, int number, ContextSheet sheet, String label,
            String rationale, String answer, boolean skip, Instant askedAt, Instant now) {
        return new ContextQuestion(id, projectId, number, sheet, label, rationale, answer,
                skip ? State.SKIPPED : State.ANSWERED, askedAt, now);
    }

    /** Nouvelle réponse ; une question passée devient répondue, ou l'inverse. */
    public ContextQuestion reanswered(String newAnswer, boolean skip, Instant now) {
        return new ContextQuestion(id, projectId, number, sheet, label, rationale, newAnswer,
                skip ? State.SKIPPED : State.ANSWERED, askedAt, now);
    }

    public String ref() {
        return ref(number);
    }

    public static String ref(int number) {
        return "Q-" + number;
    }

    private static String validAnswer(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > MAX_ANSWER) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "La réponse doit faire entre 1 et 2 000 caractères ; sinon, passez la question.");
        }
        return value;
    }

    private static String bounded(String raw, int max) {
        String value = raw == null ? "" : raw.strip();
        return value.length() > max ? value.substring(0, max) : value;
    }
}
