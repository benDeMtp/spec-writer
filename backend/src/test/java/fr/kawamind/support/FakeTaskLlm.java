package fr.kawamind.support;

import fr.kawamind.domain.port.TaskLlm;
import fr.kawamind.domain.task.ProposedTasks;
import fr.kawamind.domain.task.TaskProposalRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Modèle simulé de {@code propose-tasks} : rend ce que la fonction calcule, garde les demandes. */
public class FakeTaskLlm implements TaskLlm {

    public final List<TaskProposalRequest> requests = new ArrayList<>();
    public Function<TaskProposalRequest, ProposedTasks> answer = r -> new ProposedTasks(List.of());

    @Override
    public ProposedTasks proposeTasks(TaskProposalRequest request) {
        requests.add(request);
        return answer.apply(request);
    }
}
