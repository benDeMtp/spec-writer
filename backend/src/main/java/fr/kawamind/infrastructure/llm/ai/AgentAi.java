package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/**
 * Agent conversationnel (010, 014 research R5) : un tour est une réponse en flux, avec les outils de
 * {@link AgentTools} ; le contexte du tour arrive aux outils par {@link InvocationParameters}. Le
 * framework tient la boucle d'outils ; {@link SearchCap} plafonne les recherches, et
 * {@code maxToolCallingRoundTrips} sert de butée dure.
 *
 * <p>L'extension exige une mémoire pour les outils : chaque tour a la sienne, identifiée par
 * {@code turn}, qui ne garde que les messages du tour et que l'adaptateur retire à la fin. L'historique
 * de la conversation, lui, arrive en données dans le message utilisateur.
 */
@RegisterAiService(chatLanguageModelSupplier = AppModels.Chat.class,
        streamingChatLanguageModelSupplier = AppModels.Streaming.class,
        chatMemoryProviderSupplier = AgentAi.TurnMemory.class,
        tools = AgentTools.class, maxToolCallingRoundTrips = AgentAi.MAX_ROUND_TRIPS,
        toolHallucinationStrategy = UnknownTool.class)
@ApplicationScoped
public interface AgentAi {

    /** Butée dure : 5 recherches, une analyse de périmètre, la réponse. */
    int MAX_ROUND_TRIPS = 7;

    @SystemMessage(fromResource = "prompts/converse-agent.txt")
    @UserMessage("{data}")
    TokenStream converse(@MemoryId String turn, String data, InvocationParameters parameters);

    /** Mémoire d'un seul tour : la consigne, les données, les appels d'outils et leurs résultats. */
    final class TurnMemory implements Supplier<ChatMemoryProvider> {

        static final int MAX_MESSAGES = 64;

        @Override
        public ChatMemoryProvider get() {
            return id -> MessageWindowChatMemory.builder().id(id).maxMessages(MAX_MESSAGES).build();
        }
    }
}
