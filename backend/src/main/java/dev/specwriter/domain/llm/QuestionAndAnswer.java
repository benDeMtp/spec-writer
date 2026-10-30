package dev.specwriter.domain.llm;

import dev.specwriter.domain.question.QuestionTheme;

/** Question déjà posée et sa réponse, transmises au LLM comme contexte (FR-002). */
public record QuestionAndAnswer(String questionId, QuestionTheme theme, String label, String answer, boolean assumption) {
}
