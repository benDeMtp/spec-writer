package fr.kawamind.domain.port;

import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskTracking;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tâches d'une spécification, leurs préalables, la proposition en cours et le suivi (015, V26). */
public interface TaskRepository {

    /** Tâches de la session, par position, avec leurs préalables. */
    List<SpecTask> tasks(UUID sessionId);

    /** Remplace toutes les tâches de la session (validation, ajout, réordonnancement). */
    void replaceAll(UUID sessionId, List<SpecTask> tasks);

    /** Enregistre l'état d'une tâche existante, sans toucher aux autres ni à sa position. */
    void update(SpecTask task);

    /** Descriptions relues dans Jira des tâches de la session, par tâche ; absentes si vides. */
    Map<UUID, String> descriptions(UUID sessionId);

    /** Description relue dans Jira, en Markdown ; nulle si la sous-tâche n'en a pas. */
    void describe(UUID taskId, String description);

    Optional<TaskProposal> proposal(UUID sessionId);

    void saveProposal(UUID sessionId, TaskProposal proposal, Instant at);

    void deleteProposal(UUID sessionId);

    TaskTracking tracking(UUID sessionId);

    void setTracking(UUID sessionId, TaskTracking tracking);
}
