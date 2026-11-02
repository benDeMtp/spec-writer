package dev.specwriter.domain.question;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.List;
import java.util.UUID;

/** Question du questionnaire (data-model.md, Question ; FR-004 à FR-008). */
public record Question(
        UUID id,
        int order,
        QuestionTheme theme,
        int batch,
        String label,
        String rationale,
        List<String> suggestions,
        String defaultValue,
        List<UUID> dependsOn,
        QuestionState state,
        Answer answer) {

    public static final int MAX_LABEL = 500;
    public static final int MAX_SUGGESTIONS = 5;

    /** Valeur retenue quand une question sans valeur par défaut est passée. */
    public static final String UNSPECIFIED = "Non précisé";

    public Question {
        label = label == null ? "" : label.strip();
        rationale = rationale == null ? "" : rationale.strip();
        if (label.isEmpty() || label.length() > MAX_LABEL) {
            throw invalid("Une question doit avoir un libellé de 1 à 500 caractères.");
        }
        if (rationale.isEmpty()) {
            throw invalid("Une question doit indiquer pourquoi elle est posée.");
        }
        suggestions = suggestions.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        if (suggestions.size() > MAX_SUGGESTIONS) {
            throw invalid("Une question propose 5 suggestions au maximum.");
        }
        defaultValue = defaultValue == null || defaultValue.isBlank() ? null : defaultValue.strip();
        dependsOn = List.copyOf(dependsOn);
        if ((state == QuestionState.ANSWERED || state == QuestionState.SKIPPED) != (answer != null)) {
            throw new IllegalStateException("Réponse incohérente avec l'état " + state);
        }
    }

    public boolean isActive() {
        return state != QuestionState.OBSOLETE;
    }

    /** Les contradictions doivent être tranchées : elles ne peuvent pas être passées (FR-008). */
    public boolean isContradiction() {
        return theme == QuestionTheme.CONTRADICTION;
    }

    Question answered(Answer a) {
        return with(a.assumption() ? QuestionState.SKIPPED : QuestionState.ANSWERED, a);
    }

    Question reopened() {
        return with(QuestionState.PENDING, null);
    }

    Question obsolete() {
        return with(QuestionState.OBSOLETE, answer);
    }

    private Question with(QuestionState next, Answer a) {
        Answer kept = next == QuestionState.OBSOLETE ? null : a;
        return new Question(id, order, theme, batch, label, rationale, suggestions, defaultValue, dependsOn, next,
                kept);
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.LLM_INVALID_OUTPUT, message);
    }
}
