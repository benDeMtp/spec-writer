package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/** Entrée de {@code generateSpecification}. */
public record GenerationRequest(String need, Language language, List<QuestionAndAnswer> answers) {

    public GenerationRequest {
        answers = List.copyOf(answers);
    }
}
