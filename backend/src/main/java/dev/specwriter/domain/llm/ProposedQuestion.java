package dev.specwriter.domain.llm;

import dev.specwriter.domain.question.QuestionTheme;
import java.util.List;

/** Question proposée par le LLM. {@code defaultValue} est vide quand il n'y en a pas. */
public record ProposedQuestion(
        QuestionTheme theme,
        String label,
        String rationale,
        List<String> suggestions,
        String defaultValue,
        List<String> dependsOnQuestionIds) {

    public ProposedQuestion {
        suggestions = List.copyOf(suggestions);
        dependsOnQuestionIds = List.copyOf(dependsOnQuestionIds);
    }
}
