package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/** Entrée de {@code reassessAfterChange} : la réponse modifiée et tout le contexte (FR-007). */
public record ReassessRequest(String need, Language language, List<QuestionAndAnswer> known, String changedQuestionId,
        ProjectContextInput projectContext) {

    public ReassessRequest {
        known = List.copyOf(known);
        projectContext = projectContext == null ? ProjectContextInput.EMPTY : projectContext;
    }

    public ReassessRequest(String need, Language language, List<QuestionAndAnswer> known, String changedQuestionId) {
        this(need, language, known, changedQuestionId, ProjectContextInput.EMPTY);
    }
}
