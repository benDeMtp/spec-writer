package dev.specwriter.infrastructure.llm;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Enveloppe minimale du Copilot SDK Java utilisée par l'adaptateur LLM (research.md R3, R4).
 * Chaque appel ouvre une session sans outil, puis la supprime (contracts/llm-port.md, règle 6a).
 */
public interface CopilotSdkGateway {

    /**
     * Envoie un message système et un message utilisateur séparés et renvoie le texte de la réponse.
     *
     * @param model identifiant du modèle, ou {@code null} pour le modèle par défaut du compte
     * @param responseSchema schéma JSON strict de la réponse, ou {@code null} pour du texte libre
     */
    String complete(String model, String systemPrompt, String userPrompt, Map<String, Object> responseSchema);

    /**
     * Réponse en texte libre, rédigée en flux (010, research.md R2) : chaque morceau est passé à
     * {@code deltas} ; la requête est abandonnée dès que {@code cancelled} est vrai. Renvoie le texte
     * reçu.
     */
    default String stream(String model, String systemPrompt, String userPrompt, Consumer<String> deltas,
            BooleanSupplier cancelled) {
        throw new UnsupportedOperationException("Flux non pris en charge");
    }

    /** Erreur du SDK, classée pour être convertie en erreur métier. */
    final class GatewayException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public enum Kind {
            NOT_CONNECTED,
            MODEL_UNAVAILABLE,
            QUOTA_EXHAUSTED,
            /** Le CLI ne connaît pas une méthode du SDK : il est plus ancien que lui. */
            CLI_OUTDATED,
            UNAVAILABLE
        }

        private final Kind kind;

        public GatewayException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }
}
