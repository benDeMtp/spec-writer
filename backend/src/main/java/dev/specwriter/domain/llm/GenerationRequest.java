package dev.specwriter.domain.llm;

import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.session.Language;
import java.util.List;

/**
 * Entrée de {@code generateSpecification}. {@code correction} décrit les écarts de la tentative
 * précédente lors d'une nouvelle tentative (FR-012) ; vide sinon. {@code knowledge} est la connaissance
 * du projet (research.md R24 de 002), vide s'il n'en a pas.
 */
public record GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers, String correction,
        ProjectKnowledge knowledge) {

    public GenerationRequest {
        answers = List.copyOf(answers);
        correction = correction == null ? "" : correction;
        knowledge = knowledge == null ? ProjectKnowledge.EMPTY : knowledge;
    }

    public GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers, String correction) {
        this(need, language, answers, correction, ProjectKnowledge.EMPTY);
    }

    public GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers) {
        this(need, language, answers, "", ProjectKnowledge.EMPTY);
    }

    public GenerationRequest withCorrection(String newCorrection) {
        return new GenerationRequest(need, language, answers, newCorrection, knowledge);
    }
}
