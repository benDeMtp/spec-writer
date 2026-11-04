package dev.specwriter.domain.llm;

import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Language;
import java.util.List;
import java.util.Set;

/** Entrée de {@code proposeQuestions} (contracts/llm-port.md), avec le contexte du projet (FR-009 de 002). */
public record ProposeQuestionsRequest(
        String need, Language language, List<QuestionAndAnswer> known, Set<QuestionTheme> remainingThemes,
        ProjectContextInput projectContext) {

    public ProposeQuestionsRequest {
        known = List.copyOf(known);
        remainingThemes = Set.copyOf(remainingThemes);
        projectContext = projectContext == null ? ProjectContextInput.EMPTY : projectContext;
    }

    public ProposeQuestionsRequest(String need, Language language, List<QuestionAndAnswer> known,
            Set<QuestionTheme> remainingThemes) {
        this(need, language, known, remainingThemes, ProjectContextInput.EMPTY);
    }
}
