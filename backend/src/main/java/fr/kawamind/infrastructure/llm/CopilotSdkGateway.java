package fr.kawamind.infrastructure.llm;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Enveloppe minimale du Copilot SDK Java utilisée par les adaptateurs LLM (014, contracts/llm-adapter.md).
 * Chaque appel ouvre une session neuve et verrouillée (research R8), puis la supprime.
 */
public interface CopilotSdkGateway {

    /**
     * Outil proposé au modèle : il est déclaré terminal et sans permission, et son appel est capturé
     * pour être rendu au framework, qui l'exécute dans l'application (research R2).
     */
    record ToolSpec(String name, String description, Map<String, Object> parameters) {
    }

    /** Appel d'outil demandé par le modèle, tel que reçu : identifiant, nom et arguments JSON. */
    record CapturedCall(String id, String name, String arguments) {
    }

    /**
     * Un appel au modèle.
     *
     * @param model identifiant du modèle, ou {@code null} pour le modèle par défaut du compte
     * @param schema schéma JSON strict de la réponse, ou {@code null} pour du texte libre
     * @param tools outils proposés, vide pour une opération sans outil
     * @param deltas morceaux de la réponse en flux, ou {@code null} pour une réponse d'un bloc
     * @param reasoning réflexion du modèle, ou {@code null} pour ne pas la demander
     * @param cancelled arrêt demandé : la requête en cours est abandonnée
     */
    record Call(String model, String system, String user, Map<String, Object> schema, List<ToolSpec> tools,
            Consumer<String> deltas, Consumer<String> reasoning, BooleanSupplier cancelled) {

        public Call {
            tools = tools == null ? List.of() : List.copyOf(tools);
            cancelled = cancelled == null ? () -> false : cancelled;
        }

        /** Réponse d'un bloc, sans outil. */
        public static Call of(String model, String system, String user, Map<String, Object> schema) {
            return new Call(model, system, user, schema, List.of(), null, null, null);
        }
    }

    /** Réponse du modèle : un texte, ou des appels d'outils (le texte éventuel du tour est ignoré). */
    record Reply(String text, List<CapturedCall> toolCalls) {

        public Reply {
            text = text == null ? "" : text;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    Reply call(Call call);

    /** Réponse d'un bloc, sans outil : le texte rendu par le modèle. */
    default String complete(String model, String systemPrompt, String userPrompt, Map<String, Object> responseSchema) {
        return call(new Call(model, systemPrompt, userPrompt, responseSchema, List.of(), null,
                ReasoningSink.current().orElse(null), null)).text();
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
