package dev.specwriter.domain.llm;

import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.session.Language;
import java.util.List;

/** Entrée de {@code reassessAfterChange} : la réponse modifiée et tout le contexte (FR-007). */
public record ReassessRequest(String need, Language language, List<QuestionAndAnswer> known, String changedQuestionId,
        ProjectKnowledge knowledge) {

    public ReassessRequest {
        known = List.copyOf(known);
        knowledge = knowledge == null ? ProjectKnowledge.EMPTY : knowledge;
    }

    public ReassessRequest(String need, Language language, List<QuestionAndAnswer> known, String changedQuestionId) {
        this(need, language, known, changedQuestionId, ProjectKnowledge.EMPTY);
    }
}
