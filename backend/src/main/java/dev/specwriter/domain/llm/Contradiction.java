package dev.specwriter.domain.llm;

/** Contradiction détectée entre le besoin et les réponses (FR-008). */
public record Contradiction(String description, String questionLabel) {
}
