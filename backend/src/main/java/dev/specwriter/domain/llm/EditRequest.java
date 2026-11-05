package dev.specwriter.domain.llm;

import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.session.Language;
import java.util.List;

/**
 * Entrée de {@code editSpecification} (FR-044). La spécification actuelle est passée en Markdown,
 * avec le besoin et les réponses que ses sources citent. L'instruction est une donnée, jamais une
 * consigne système.
 */
public record EditRequest(String need, Language language, List<QuestionAndAnswer> answers, String currentMarkdown,
        String instruction, String correction, ProjectKnowledge knowledge) {

    public EditRequest {
        answers = List.copyOf(answers);
        knowledge = knowledge == null ? ProjectKnowledge.EMPTY : knowledge;
    }

    public EditRequest(String need, Language language, List<QuestionAndAnswer> answers, String currentMarkdown,
            String instruction, String correction) {
        this(need, language, answers, currentMarkdown, instruction, correction, ProjectKnowledge.EMPTY);
    }

    public EditRequest withCorrection(String newCorrection) {
        return new EditRequest(need, language, answers, currentMarkdown, instruction, newCorrection, knowledge);
    }
}
