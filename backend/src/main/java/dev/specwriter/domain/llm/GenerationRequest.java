package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/**
 * Entrée de {@code generateSpecification}. {@code correction} décrit les écarts de la tentative
 * précédente lors d'une nouvelle tentative (FR-012) ; vide sinon.
 */
public record GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers, String correction) {

    public GenerationRequest {
        answers = List.copyOf(answers);
        correction = correction == null ? "" : correction;
    }

    public GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers) {
        this(need, language, answers, "");
    }

    public GenerationRequest withCorrection(String newCorrection) {
        return new GenerationRequest(need, language, answers, newCorrection);
    }
}
