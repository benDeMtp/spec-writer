package fr.kawamind.infrastructure.llm;

import fr.kawamind.domain.port.TaskLlm;
import fr.kawamind.domain.task.ProposedTasks;
import fr.kawamind.domain.task.TaskProposalRequest;
import fr.kawamind.infrastructure.llm.ai.TasksAi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Port {@link TaskLlm} sur l'AI Service {@link TasksAi} (015) : données balisées, schéma strict. */
@ApplicationScoped
public class LangChainTaskLlm implements TaskLlm {

    private final TasksAi tasks;
    private final Duration timeout;

    @Inject
    public LangChainTaskLlm(TasksAi tasks,
            @ConfigProperty(name = "specwriter.llm.timeout.interactive", defaultValue = "180s") Duration timeout) {
        this.tasks = tasks;
        this.timeout = timeout;
    }

    @Override
    public ProposedTasks proposeTasks(TaskProposalRequest request) {
        return AiCalls.call(timeout, () -> tasks.propose(UserMessages.tasks(request), LlmSchemas.params(LlmSchemas.TASKS)));
    }
}
