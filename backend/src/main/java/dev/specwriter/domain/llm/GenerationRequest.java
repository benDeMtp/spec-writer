package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/**
 * Entrée de {@code generateSpecification}. {@code correction} décrit les écarts de la tentative
 * précédente lors d'une nouvelle tentative (FR-012) ; vide sinon. {@code projectContext} est la fiche
 * de contexte du projet, vide s'il n'en a pas (FR-009 de 002).
 */
public record GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers, String correction,
        String projectContext) {

    public GenerationRequest {
        answers = List.copyOf(answers);
        correction = correction == null ? "" : correction;
        projectContext = projectContext == null ? "" : projectContext;
    }

    public GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers, String correction) {
        this(need, language, answers, correction, "");
    }

    public GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers) {
        this(need, language, answers, "", "");
    }

    public GenerationRequest withCorrection(String newCorrection) {
        return new GenerationRequest(need, language, answers, newCorrection, projectContext);
    }
}
