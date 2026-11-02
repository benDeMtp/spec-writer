package dev.specwriter.domain.llm;

import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Language;
import java.util.List;
import java.util.Set;

/** Entrée de {@code proposeQuestions} (contracts/llm-port.md). */
public record ProposeQuestionsRequest(
        String need, Language language, List<QuestionAndAnswer> known, Set<QuestionTheme> remainingThemes) {

    public ProposeQuestionsRequest {
        known = List.copyOf(known);
        remainingThemes = Set.copyOf(remainingThemes);
    }
}
