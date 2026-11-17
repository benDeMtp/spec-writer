package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import fr.kawamind.domain.task.ProposedTasks;
import fr.kawamind.infrastructure.llm.ai.guardrails.TasksGuardrail;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Proposition de tâches d'une spécification (015, R8) : consigne {@code propose-tasks} en système,
 * qui déclare la consigne réutilisable {@code decoupage-taches} ; données balisées par
 * {@code UserMessages.tasks}.
 */
@RegisterAiService(chatLanguageModelSupplier = AppModels.Chat.class,
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface TasksAi {

    @SystemMessage(fromResource = "prompts/propose-tasks.txt")
    @UserMessage("{data}")
    @OutputGuardrails(TasksGuardrail.class)
    ProposedTasks propose(String data, ChatRequestParameters format);
}
