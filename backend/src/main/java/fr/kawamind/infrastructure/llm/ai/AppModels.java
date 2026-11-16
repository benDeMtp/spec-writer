package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkus.arc.Arc;
import java.util.function.Supplier;

/**
 * Modèles des AI Services (014, research R3) : ceux que produit {@code ChatModelProducer} (Copilot
 * ou rejeu), et non ceux d'un fournisseur de l'extension. La référence est le mandataire CDI du
 * bean, si bien que les tests peuvent le remplacer.
 */
public final class AppModels {

    private AppModels() {
    }

    /** Modèle d'un bloc. */
    public static final class Chat implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return Arc.container().instance(ChatModel.class).get();
        }
    }

    /** Modèle en flux. */
    public static final class Streaming implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return Arc.container().instance(StreamingChatModel.class).get();
        }
    }
}
