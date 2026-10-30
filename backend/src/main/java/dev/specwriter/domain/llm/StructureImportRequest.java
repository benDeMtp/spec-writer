package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;

/** Entrée de {@code structureImportedText} : texte d'un ticket Jira importé (data-model.md). */
public record StructureImportRequest(String text, Language language) {
}
