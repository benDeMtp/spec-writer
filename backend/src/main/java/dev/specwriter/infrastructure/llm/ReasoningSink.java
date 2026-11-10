package dev.specwriter.infrastructure.llm;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Destinataire de la réflexion du modèle pour les appels en cours sur ce fil (010), fixé par
 * {@link LangChainConversationLlm#thinking} et lu par {@link CopilotRuntime} et le rejeu : le
 * {@code ChatModel} de LangChain4j n'a pas de paramètre pour le porter. Hors de cette portée, aucune
 * réflexion n'est demandée au modèle.
 */
public final class ReasoningSink {

    static final ScopedValue<Consumer<String>> CURRENT = ScopedValue.newInstance();

    private ReasoningSink() {
    }

    public static Optional<Consumer<String>> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }
}
