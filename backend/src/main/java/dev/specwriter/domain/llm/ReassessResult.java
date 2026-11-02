package dev.specwriter.domain.llm;

import java.util.List;

/** Sortie de {@code reassessAfterChange}. */
public record ReassessResult(List<String> obsoleteQuestionIds, List<ProposedQuestion> newQuestions) {

    public ReassessResult {
        obsoleteQuestionIds = List.copyOf(obsoleteQuestionIds);
        newQuestions = List.copyOf(newQuestions);
    }
}
