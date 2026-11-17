package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import fr.kawamind.domain.conversation.NeedDraft;
import fr.kawamind.infrastructure.llm.ai.guardrails.NeedGuardrail;
import fr.kawamind.infrastructure.llm.ai.guardrails.QualificationGuardrail;
import fr.kawamind.infrastructure.llm.ai.guardrails.SummaryGuardrail;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Opérations structurées de la conversation (010, 013, 014 research R3) : qualification d'une analyse
 * de périmètre, besoin rédigé depuis la discussion, résumé de la mémoire. L'agent lui-même est
 * {@link AgentAi}.
 */
@RegisterAiService(chatLanguageModelSupplier = AppModels.Chat.class,
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface ConversationAi {

    @SystemMessage(fromResource = "prompts/qualify-perimeter.txt")
    @UserMessage("{data}")
    @OutputGuardrails(QualificationGuardrail.class)
    LlmOutputs.Qualifications qualify(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/propose-need.txt")
    @UserMessage("{data}")
    @OutputGuardrails(NeedGuardrail.class)
    NeedDraft draftNeed(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/compact-memory.txt")
    @UserMessage("{data}")
    @OutputGuardrails(SummaryGuardrail.class)
    LlmOutputs.Summary compact(String data, ChatRequestParameters format);
}
