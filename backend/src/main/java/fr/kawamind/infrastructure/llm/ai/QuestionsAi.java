package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import fr.kawamind.domain.llm.ProposeQuestionsResult;
import fr.kawamind.domain.llm.ReassessResult;
import fr.kawamind.infrastructure.llm.ai.guardrails.QuestionsGuardrail;
import fr.kawamind.infrastructure.llm.ai.guardrails.ReassessGuardrail;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/** Questions de clarification (001, 014 research R3) : proposition, puis réévaluation après un changement. */
@RegisterAiService(chatLanguageModelSupplier = AppModels.Chat.class,
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface QuestionsAi {

    @SystemMessage(fromResource = "prompts/propose-questions.txt")
    @UserMessage("{data}")
    @OutputGuardrails(QuestionsGuardrail.class)
    ProposeQuestionsResult propose(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/reassess-after-change.txt")
    @UserMessage("{data}")
    @OutputGuardrails(ReassessGuardrail.class)
    ReassessResult reassess(String data, ChatRequestParameters format);
}
