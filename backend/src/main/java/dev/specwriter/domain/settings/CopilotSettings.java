package dev.specwriter.domain.settings;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Réglages GitHub Copilot, sans aucun secret (data-model.md, CopilotSettings). Le jeton est détenu
 * par le runtime Copilot (voie A, research.md R4) ou par le trousseau (voie B).
 */
public record CopilotSettings(
        String connectedLogin,
        SubscriptionState subscriptionState,
        AuthMode authMode,
        String model,
        List<String> availableModels,
        List<String> modelPreference,
        Instant tokenExpiresAt,
        SecretPersistence secretPersistence,
        List<ModelCapacity> capacities) {

    /** Préférence par défaut : modèles observés et autorisés pendant le spike S1. */
    public static final List<String> DEFAULT_PREFERENCE =
            List.of("claude-sonnet-5.5", "gpt-6.1-sol", "claude-sonnet-5", "gpt-6-luna", "auto");

    public enum SubscriptionState {
        UNKNOWN,
        ACTIVE,
        NO_COPILOT
    }

    public enum AuthMode {
        COPILOT_CLI,
        GITHUB_APP
    }

    public CopilotSettings {
        capacities = capacities == null ? List.of() : List.copyOf(capacities);
        availableModels = List.copyOf(availableModels);
        modelPreference = List.copyOf(modelPreference);
        if (model != null && !availableModels.contains(model)) {
            throw new DomainException(ErrorCode.MODEL_UNAVAILABLE);
        }
    }

    public CopilotSettings(String connectedLogin, SubscriptionState subscriptionState, AuthMode authMode, String model,
            List<String> availableModels, List<String> modelPreference, Instant tokenExpiresAt,
            SecretPersistence secretPersistence) {
        this(connectedLogin, subscriptionState, authMode, model, availableModels, modelPreference, tokenExpiresAt,
                secretPersistence, List.of());
    }

    public static CopilotSettings initial() {
        return new CopilotSettings(null, SubscriptionState.UNKNOWN, AuthMode.COPILOT_CLI, null, List.of(),
                DEFAULT_PREFERENCE, null, SecretPersistence.KEYCHAIN);
    }

    /** Après connexion : modèles autorisés relus ; le modèle choisi est conservé s'il reste disponible. */
    public CopilotSettings connected(String login, List<String> models) {
        return connected(login, models, List.of());
    }

    /** Après connexion, avec la capacité de chaque modèle autorisé (008, research.md R1). */
    public CopilotSettings connected(String login, List<String> models, List<ModelCapacity> modelCapacities) {
        if (models.isEmpty()) {
            return new CopilotSettings(login, SubscriptionState.NO_COPILOT, authMode, null, List.of(), modelPreference,
                    tokenExpiresAt, secretPersistence, List.of());
        }
        String keep = model != null && models.contains(model) ? model : defaultModel(models, modelPreference).orElse(null);
        return new CopilotSettings(login, SubscriptionState.ACTIVE, authMode, keep, models, modelPreference,
                tokenExpiresAt, secretPersistence, modelCapacities);
    }

    /** Capacité du modèle choisi, si elle est connue. */
    public Optional<ModelCapacity> capacity() {
        return model == null ? Optional.empty() : capacities.stream().filter(c -> c.id().equals(model)).findFirst();
    }

    public CopilotSettings disconnected() {
        return new CopilotSettings(null, SubscriptionState.UNKNOWN, authMode, null, List.of(), modelPreference, null,
                secretPersistence, List.of());
    }

    /** Choix explicite de l'utilisateur (FR-030c) ; refusé si le modèle n'est pas autorisé. */
    public CopilotSettings withModel(String chosen) {
        if (!availableModels.contains(chosen)) {
            throw new DomainException(ErrorCode.MODEL_UNAVAILABLE);
        }
        return new CopilotSettings(connectedLogin, subscriptionState, authMode, chosen, availableModels,
                modelPreference, tokenExpiresAt, secretPersistence, capacities);
    }

    /** Premier modèle de la préférence présent dans les modèles autorisés, sinon le premier autorisé. */
    static Optional<String> defaultModel(List<String> available, List<String> preference) {
        return preference.stream().filter(available::contains).findFirst()
                .or(() -> available.stream().findFirst());
    }
}
