package dev.specwriter.domain.llm;

import dev.specwriter.domain.question.QuestionTheme;
import java.util.List;
import java.util.Set;

/**
 * Sortie de {@code proposeQuestions} : 5 questions au maximum par lot (FR-004), contradictions
 * détectées (FR-008) et thèmes que le besoin renseigne déjà, à ne pas questionner (FR-002).
 */
public record ProposeQuestionsResult(List<ProposedQuestion> questions, List<Contradiction> contradictions,
        Set<QuestionTheme> coveredThemes) {

    public ProposeQuestionsResult {
        questions = List.copyOf(questions);
        contradictions = List.copyOf(contradictions);
        coveredThemes = Set.copyOf(coveredThemes);
    }
}
