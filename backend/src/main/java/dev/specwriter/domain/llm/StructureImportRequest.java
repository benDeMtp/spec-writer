package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;

/** Entrée de {@code structureImportedText} : texte d'un ticket Jira importé, en Markdown (data-model.md). */
public record StructureImportRequest(String text, Language language, String correction, ProjectContextInput projectContext) {

    public StructureImportRequest {
        projectContext = projectContext == null ? ProjectContextInput.EMPTY : projectContext;
    }

    public StructureImportRequest(String text, Language language, String correction) {
        this(text, language, correction, ProjectContextInput.EMPTY);
    }

    public StructureImportRequest withCorrection(String newCorrection) {
        return new StructureImportRequest(text, language, newCorrection, projectContext);
    }
}
