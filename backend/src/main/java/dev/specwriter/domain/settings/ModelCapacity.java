package dev.specwriter.domain.settings;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Capacité d'un modèle Copilot (008, data-model.md) : limite de prompt et fenêtre de contexte en
 * tokens, coefficient de facturation. Une valeur absente ou aberrante est inconnue (FR-002).
 */
public record ModelCapacity(String id, String name, Integer promptTokens, Integer contextTokens, Double multiplier) {

    /** Au-delà, la limite annoncée est tenue pour aberrante. */
    public static final int MAX_TOKENS = 10_000_000;

    public ModelCapacity {
        Objects.requireNonNull(id);
        if (id.isBlank()) {
            throw new IllegalArgumentException("identifiant de modèle vide");
        }
        name = name == null || name.isBlank() ? id : name;
        promptTokens = plausible(promptTokens);
        contextTokens = plausible(contextTokens);
        multiplier = multiplier == null || multiplier.isNaN() || multiplier < 0 ? null : multiplier;
    }

    public OptionalInt prompt() {
        return promptTokens == null ? OptionalInt.empty() : OptionalInt.of(promptTokens);
    }

    /** Coefficient pour comparer les coûts : inconnu compté comme le plus cher. */
    public double cost() {
        return multiplier == null ? Double.MAX_VALUE : multiplier;
    }

    private static Integer plausible(Integer tokens) {
        return tokens == null || tokens <= 0 || tokens > MAX_TOKENS ? null : tokens;
    }
}
