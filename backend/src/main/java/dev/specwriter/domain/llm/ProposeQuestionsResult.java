package dev.specwriter.domain.llm;

import java.util.List;

/** Sortie de {@code proposeQuestions} : 5 questions au maximum par lot (FR-004). */
public record ProposeQuestionsResult(List<ProposedQuestion> questions, List<Contradiction> contradictions) {

    public ProposeQuestionsResult {
        questions = List.copyOf(questions);
        contradictions = List.copyOf(contradictions);
    }
}
