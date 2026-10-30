package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;

/** Entrée de {@code structureImportedText} : texte d'un ticket Jira importé, en Markdown (data-model.md). */
public record StructureImportRequest(String text, Language language, String correction) {

    public StructureImportRequest withCorrection(String newCorrection) {
        return new StructureImportRequest(text, language, newCorrection);
    }
}
