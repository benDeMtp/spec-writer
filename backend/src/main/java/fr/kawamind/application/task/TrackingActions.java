package fr.kawamind.application.task;

import fr.kawamind.application.agent.AgentJournal;
import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.StatusCategory;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.domain.task.TaskTracking;
import java.util.UUID;

/**
 * Actions de l'utilisateur sur le suivi (015, US3) : libérer une tâche tenue par un agent (FR-022),
 * « Synchroniser » (FR-013a), « Reprendre le suivi » (FR-020a).
 */
public class TrackingActions {

    private final TaskDesk desk;
    private final TaskSync sync;
    private final TaskJira jira;
    private final ReadTasks read;
    private final AgentJournal journal;

    public TrackingActions(TaskDesk desk, TaskSync sync, TaskJira jira, ReadTasks read, AgentJournal journal) {
        this.desk = desk;
        this.sync = sync;
        this.jira = jira;
        this.read = read;
        this.journal = journal;
    }

    /** La tâche redevient à faire dans spec-writer et dans Jira ; Jira injoignable la met « à synchroniser ». */
    public TasksView release(UUID sessionId, UUID taskId) {
        Session session = desk.sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        boolean problem = desk.locks.with(sessionId, () -> {
            SpecTask task = desk.tasks.tasks(sessionId).stream().filter(t -> t.id().equals(taskId)).findFirst()
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            if (task.status() != TaskStatus.IN_PROGRESS || !task.isCreated()) {
                throw new DomainException(ErrorCode.TASK_NOT_READY, "Seule une tâche en cours peut être libérée.");
            }
            TaskJira.Result result = jira.moveTo(desk.endpoint(sessionId), task.jiraKey(), StatusCategory.NEW);
            desk.tasks.update(task.released(result == TaskJira.Result.UNREACHABLE));
            if (task.holder() != null) {
                journal.recordTask(task.holder().agentName(), AgentOperation.RELEASE_TASK, session.projectId(),
                        sessionId, task.jiraKey());
            }
            sync.applyStatus(sessionId);
            return result == TaskJira.Result.UNREACHABLE;
        });
        return read.view(sessionId, problem);
    }

    public TasksView sync(UUID sessionId) {
        desk.requireSession(sessionId);
        boolean problem = desk.locks.with(sessionId, () -> sync.refresh(sessionId));
        return read.view(sessionId, problem);
    }

    /** Reprend le suivi suspendu ; le statut est recalculé d'après les tâches. */
    public TasksView resume(UUID sessionId) {
        desk.requireSession(sessionId);
        boolean problem = desk.locks.with(sessionId, () -> {
            desk.tasks.setTracking(sessionId, TaskTracking.ACTIVE);
            if (desk.sessions.implementation(sessionId)
                    .filter(i -> i.status() == ImplementationStatus.DRAFT).isPresent()) {
                // un brouillon repart de « prête » : les tâches décident ensuite
                desk.sessions.saveImplementation(sessionId, desk.sessions.implementation(sessionId).orElseThrow()
                        .change(ImplementationStatus.READY,
                                ImplementationStatus.Actor.USER, false, desk.clock.now()));
            }
            return sync.refresh(sessionId);
        });
        return read.view(sessionId, problem);
    }
}
