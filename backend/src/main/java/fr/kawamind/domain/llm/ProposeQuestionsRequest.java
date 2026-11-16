package fr.kawamind.domain.llm;

import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.question.QuestionTheme;
import fr.kawamind.domain.session.Language;
import java.util.List;
import java.util.Set;

/** Entrée de {@code proposeQuestions} (contracts/llm-port.md), avec le contexte du projet (FR-009 de 002). */
public record ProposeQuestionsRequest(
        String need, Language language, List<QuestionAndAnswer> known, Set<QuestionTheme> remainingThemes,
        ProjectKnowledge knowledge) {

    public ProposeQuestionsRequest {
        known = List.copyOf(known);
        remainingThemes = Set.copyOf(remainingThemes);
        knowledge = knowledge == null ? ProjectKnowledge.EMPTY : knowledge;
    }

    public ProposeQuestionsRequest(String need, Language language, List<QuestionAndAnswer> known,
            Set<QuestionTheme> remainingThemes) {
        this(need, language, known, remainingThemes, ProjectKnowledge.EMPTY);
    }
}
