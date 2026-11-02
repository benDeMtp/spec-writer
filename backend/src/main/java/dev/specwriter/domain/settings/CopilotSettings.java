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
        SecretPersistence secretPersistence) {

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
        availableModels = List.copyOf(availableModels);
        modelPreference = List.copyOf(modelPreference);
        if (model != null && !availableModels.contains(model)) {
            throw new DomainException(ErrorCode.MODEL_UNAVAILABLE);
        }
    }

    public static CopilotSettings initial() {
        return new CopilotSettings(null, SubscriptionState.UNKNOWN, AuthMode.COPILOT_CLI, null, List.of(),
                DEFAULT_PREFERENCE, null, SecretPersistence.KEYCHAIN);
    }

    /** Après connexion : modèles autorisés relus ; le modèle choisi est conservé s'il reste disponible. */
    public CopilotSettings connected(String login, List<String> models) {
        if (models.isEmpty()) {
            return new CopilotSettings(login, SubscriptionState.NO_COPILOT, authMode, null, List.of(), modelPreference,
                    tokenExpiresAt, secretPersistence);
        }
        String keep = model != null && models.contains(model) ? model : defaultModel(models, modelPreference).orElse(null);
        return new CopilotSettings(login, SubscriptionState.ACTIVE, authMode, keep, models, modelPreference,
                tokenExpiresAt, secretPersistence);
    }

    public CopilotSettings disconnected() {
        return new CopilotSettings(null, SubscriptionState.UNKNOWN, authMode, null, List.of(), modelPreference, null,
                secretPersistence);
    }

    /** Choix explicite de l'utilisateur (FR-030c) ; refusé si le modèle n'est pas autorisé. */
    public CopilotSettings withModel(String chosen) {
        if (!availableModels.contains(chosen)) {
            throw new DomainException(ErrorCode.MODEL_UNAVAILABLE);
        }
        return new CopilotSettings(connectedLogin, subscriptionState, authMode, chosen, availableModels,
                modelPreference, tokenExpiresAt, secretPersistence);
    }

    /** Premier modèle de la préférence présent dans les modèles autorisés, sinon le premier autorisé. */
    static Optional<String> defaultModel(List<String> available, List<String> preference) {
        return preference.stream().filter(available::contains).findFirst()
                .or(() -> available.stream().findFirst());
    }
}
