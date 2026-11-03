package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;

/** Entrée de {@code structureImportedText} : texte d'un ticket Jira importé, en Markdown (data-model.md). */
public record StructureImportRequest(String text, Language language, String correction, String projectContext) {

    public StructureImportRequest {
        projectContext = projectContext == null ? "" : projectContext;
    }

    public StructureImportRequest(String text, Language language, String correction) {
        this(text, language, correction, "");
    }

    public StructureImportRequest withCorrection(String newCorrection) {
        return new StructureImportRequest(text, language, newCorrection, projectContext);
    }
}
