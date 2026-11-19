package fr.kawamind.domain.port;

import fr.kawamind.domain.task.ProposedTasks;
import fr.kawamind.domain.task.TaskProposalRequest;

/**
 * Proposition de tâches par le modèle (015, opération {@code propose-tasks}). Lève
 * une {@link fr.kawamind.domain.error.DomainException} comme {@link LlmClient} ; une sortie toujours non conforme donne {@code LLM_INVALID_OUTPUT}.
 */
public interface TaskLlm {

    ProposedTasks proposeTasks(TaskProposalRequest request);
}
