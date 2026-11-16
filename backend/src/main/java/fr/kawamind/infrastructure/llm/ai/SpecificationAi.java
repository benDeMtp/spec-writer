package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.port.ConversationLlm.Revision;
import fr.kawamind.infrastructure.llm.ai.guardrails.ChangesGuardrail;
import fr.kawamind.infrastructure.llm.ai.guardrails.RevisionGuardrail;
import fr.kawamind.infrastructure.llm.ai.guardrails.SpecificationGuardrail;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Rédaction de la spécification (001, 011, 014 research R3) : génération, modification, fusion,
 * structuration d'un texte importé, nouvelle version d'après une discussion, relevé des changements.
 */
@RegisterAiService(chatLanguageModelSupplier = AppModels.Chat.class,
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface SpecificationAi {

    @SystemMessage(fromResource = "prompts/generate-specification.txt")
    @UserMessage("{data}")
    @OutputGuardrails(SpecificationGuardrail.class)
    DraftSpecification generate(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/edit-specification.txt")
    @UserMessage("{data}")
    @OutputGuardrails(SpecificationGuardrail.class)
    DraftSpecification edit(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/merge-conflict.txt")
    @UserMessage("{data}")
    @OutputGuardrails(SpecificationGuardrail.class)
    DraftSpecification merge(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/structure-imported-text.txt")
    @UserMessage("{data}")
    @OutputGuardrails(SpecificationGuardrail.class)
    DraftSpecification structure(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/revise-specification.txt")
    @UserMessage("{data}")
    @OutputGuardrails(RevisionGuardrail.class)
    Revision revise(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/list-changes.txt")
    @UserMessage("{data}")
    @OutputGuardrails(ChangesGuardrail.class)
    LlmOutputs.Changes changes(String data, ChatRequestParameters format);
}
