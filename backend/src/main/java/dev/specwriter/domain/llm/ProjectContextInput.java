package dev.specwriter.domain.llm;

import dev.specwriter.domain.project.ContextSheets;

/**
 * Fiches du projet transmises aux opérations sur les spécifications (FR-008a, FR-009) : vides si
 * le projet n'en a pas, auquel cas le message est celui de 001.
 */
public record ProjectContextInput(String functional, String technical) {

    public static final ProjectContextInput EMPTY = new ProjectContextInput("", "");

    public ProjectContextInput {
        functional = functional == null ? "" : functional;
        technical = technical == null ? "" : technical;
    }

    public static ProjectContextInput of(ContextSheets sheets) {
        return new ProjectContextInput(sheets.functional().markdown(), sheets.technical().markdown());
    }

    public boolean isEmpty() {
        return functional.isBlank() && technical.isBlank();
    }

    /** Les deux fiches, où une source PROJECT_CONTEXT doit trouver son extrait (FR-010). */
    public String combined() {
        return functional + "\n\n" + technical;
    }
}
