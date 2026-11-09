package dev.specwriter.domain.conversation;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.question.QuestionTheme;
import java.util.Objects;

/**
 * Point établi par une conversation (010, US2) : une question et sa réponse, relues par
 * l'utilisateur, qui deviennent une réponse déjà donnée du questionnaire. {@code basis} dit d'où il
 * vient : l'utilisateur l'a dit, ou une source citée l'établit ({@code sourceRef}).
 */
public record EstablishedPoint(QuestionTheme theme, String question, String answer, Basis basis, String sourceRef) {

    public static final int MAX_QUESTION = 500;
    public static final int MAX_ANSWER = 5_000;

    public enum Basis {
        USER, SOURCE
    }

    public EstablishedPoint {
        Objects.requireNonNull(theme);
        question = question == null ? "" : question.strip();
        answer = answer == null ? "" : answer.strip();
        basis = basis == null ? Basis.USER : basis;
        sourceRef = basis == Basis.SOURCE && sourceRef != null && !sourceRef.isBlank() ? sourceRef.strip() : null;
        if (question.isEmpty() || answer.isEmpty() || question.length() > MAX_QUESTION || answer.length() > MAX_ANSWER) {
            throw new DomainException(ErrorCode.NEED_DRAFT_INVALID,
                    "Chaque point établi a une question (500 caractères au plus) et une réponse (5 000 au plus).");
        }
    }
}
