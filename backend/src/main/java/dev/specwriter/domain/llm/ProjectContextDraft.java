package dev.specwriter.domain.llm;

import dev.specwriter.domain.project.ContextSheet;
import java.util.List;

/**
 * Sortie de {@code buildProjectContext} : les deux fiches proposées, en Markdown, et les questions
 * de clarification sur ce qui n'est pas clair (FR-013h).
 */
public record ProjectContextDraft(String functional, String technical, List<Question> questions) {

    public record Question(ContextSheet sheet, String label, String rationale, List<String> suggestions) {

        public Question {
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        }
    }

    public ProjectContextDraft {
        functional = functional == null ? "" : functional.strip();
        technical = technical == null ? "" : technical.strip();
        questions = questions == null ? List.of() : List.copyOf(questions);
    }

    public String get(ContextSheet sheet) {
        return sheet == ContextSheet.FUNCTIONAL ? functional : technical;
    }
}
