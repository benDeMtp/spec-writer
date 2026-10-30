package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/**
 * Entrée de {@code editSpecification} (FR-044). La spécification actuelle est passée en Markdown,
 * avec le besoin et les réponses que ses sources citent. L'instruction est une donnée, jamais une
 * consigne système.
 */
public record EditRequest(String need, Language language, List<QuestionAndAnswer> answers, String currentMarkdown,
        String instruction, String correction) {

    public EditRequest {
        answers = List.copyOf(answers);
    }

    public EditRequest withCorrection(String newCorrection) {
        return new EditRequest(need, language, answers, currentMarkdown, instruction, newCorrection);
    }
}
