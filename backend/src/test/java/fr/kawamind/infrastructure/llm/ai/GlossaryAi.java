package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * AI Service de test (014, US3) : une capacité ajoutée par déclaration seulement. Consigne en
 * ressource avec une consigne réutilisable, un outil, le garde-fou du plafond.
 */
@RegisterAiService(chatLanguageModelSupplier = AppModels.Chat.class, chatMemoryProviderSupplier = AgentAi.TurnMemory.class,
        tools = GlossaryTool.class, maxToolCallingRoundTrips = AgentAi.MAX_ROUND_TRIPS,
        toolHallucinationStrategy = UnknownTool.class)
@ApplicationScoped
public interface GlossaryAi {

    @SystemMessage(fromResource = "prompts/lookup-glossary.txt")
    @UserMessage("{data}")
    String ask(@MemoryId String turn, String data, InvocationParameters parameters);
}
