package fr.kawamind.application.agent;

import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.TaskRepository;
import fr.kawamind.domain.task.TaskProgress;
import fr.kawamind.domain.session.Implementation;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Session;
import java.util.UUID;

/**
 * Outil MCP {@code set_feature_status} (FR-027) : READY → IN_PROGRESS et IN_PROGRESS → IMPLEMENTED
 * seulement. Chaque changement est consigné avec l'ancien et le nouveau statut ; l'utilisateur peut
 * rétablir le précédent.
 */
public class AgentChangeStatus {

    public record Changed(UUID id, ImplementationStatus status) {
    }

    private final SessionRepository sessions;
    private final ClockPort clock;
    private final AgentJournal journal;
    private final TaskRepository tasks;

    public AgentChangeStatus(SessionRepository sessions, ClockPort clock, AgentJournal journal) {
        this(sessions, clock, journal, null);
    }

    /** {@code tasks} : une feature qui a des tâches suit leur avancement (015, R7). */
    public AgentChangeStatus(SessionRepository sessions, ClockPort clock, AgentJournal journal, TaskRepository tasks) {
        this.tasks = tasks;
        this.sessions = sessions;
        this.clock = clock;
        this.journal = journal;
    }

    public Changed run(String agentName, UUID featureId, ImplementationStatus target) {
        if (target == null) {
            throw new DomainException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        Session session = sessions.find(featureId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (tasks != null && TaskProgress.total(tasks.tasks(featureId)) > 0) {
            throw new DomainException(ErrorCode.STATUS_FOLLOWS_TASKS, "Cette feature a des tâches : son statut suit leur "
                    + "avancement. Utilisez next_task, start_task et complete_task.");
        }
        Implementation current = sessions.implementation(featureId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Implementation next = current.change(target, ImplementationStatus.Actor.AGENT, false, clock.now());
        if (!next.equals(current)) {
            sessions.saveImplementation(featureId, next);
            journal.record(agentName, AgentOperation.SET_STATUS, session.projectId(), featureId, current.status(),
                    next.status());
        }
        return new Changed(featureId, next.status());
    }
}
