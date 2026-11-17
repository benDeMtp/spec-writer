package fr.kawamind.infrastructure.llm;

import java.util.function.BooleanSupplier;

/**
 * Arrêt demandé pour les appels en flux en cours sur ce fil (014, research R5), fixé par
 * {@link LangChainConversationLlm} et lu par les adaptateurs en flux : un tour d'agent passe par
 * plusieurs appels au modèle, et le {@code StreamingChatModel} de LangChain4j n'a pas de paramètre
 * pour le porter.
 */
public final class CallCancellation {

    static final ScopedValue<BooleanSupplier> CURRENT = ScopedValue.newInstance();

    private CallCancellation() {
    }

    public static boolean requested() {
        return CURRENT.isBound() && CURRENT.get().getAsBoolean();
    }
}
