package dev.specwriter.infrastructure.llm;

import java.util.Optional;

/** Modèle Copilot à utiliser (FR-030c) ; vide pour le modèle par défaut du compte. */
@FunctionalInterface
public interface ModelSelection {
    Optional<String> currentModel();
}
