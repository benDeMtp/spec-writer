package fr.kawamind.domain.llm;

import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.session.Language;

/** Entrée de {@code structureImportedText} : texte d'un ticket Jira importé, en Markdown (data-model.md). */
public record StructureImportRequest(String text, Language language, String correction, ProjectKnowledge knowledge) {

    public StructureImportRequest {
        knowledge = knowledge == null ? ProjectKnowledge.EMPTY : knowledge;
    }

    public StructureImportRequest(String text, Language language, String correction) {
        this(text, language, correction, ProjectKnowledge.EMPTY);
    }

    public StructureImportRequest withCorrection(String newCorrection) {
        return new StructureImportRequest(text, language, newCorrection, knowledge);
    }
}
