package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/** Entrée de {@code reassessAfterChange} : la réponse modifiée et tout le contexte (FR-007). */
public record ReassessRequest(String need, Language language, List<QuestionAndAnswer> known, String changedQuestionId) {

    public ReassessRequest {
        known = List.copyOf(known);
    }
}
