package fr.kawamind.domain.port;

import fr.kawamind.domain.task.KnownStatus;
import fr.kawamind.domain.task.TaskWorkflow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Réglage du suivi des tâches d'un projet dans Jira (023, V35), et statuts Jira connus de son index. */
public interface TaskWorkflowRepository {

    Optional<TaskWorkflow> find(UUID projectId);

    void save(UUID projectId, TaskWorkflow workflow, Instant at);

    void delete(UUID projectId);

    /**
     * Statuts des tickets indexés du projet, chacun une fois (au sens de {@code StatusName.same}),
     * classés par catégorie ({@code new}, {@code indeterminate}, {@code done}) puis par nom.
     */
    List<KnownStatus> knownStatuses(UUID projectId);
}
