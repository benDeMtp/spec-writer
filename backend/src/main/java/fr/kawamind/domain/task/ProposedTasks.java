package fr.kawamind.domain.task;

import java.util.List;

/** Tâches rendues par le modèle pour {@code propose-tasks} (015, contracts/llm-propose-tasks.md). */
public record ProposedTasks(List<ProposedTask> tasks) {

    public ProposedTasks {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
