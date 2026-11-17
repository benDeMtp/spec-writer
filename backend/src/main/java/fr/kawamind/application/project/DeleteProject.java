package fr.kawamind.application.project;

import fr.kawamind.application.session.ManageSessions;
import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.session.SessionListing;
import java.util.List;
import java.util.UUID;

/**
 * Supprime un projet (FR-002) : ses sessions sont déplacées vers un autre projet ou supprimées.
 * Le dernier projet restant est conservé, et rien n'est fait pendant une opération de l'IA sur une
 * de ses sessions. Jira n'est jamais modifié (FR-017).
 */
public class DeleteProject {

    public enum Mode { MOVE, DELETE }

    private final ProjectRepository projects;
    private final SessionRepository sessions;
    private final ManageSessions manageSessions;
    private final OperationRegistry operations;
    private final ClockPort clock;

    public DeleteProject(ProjectRepository projects, SessionRepository sessions, ManageSessions manageSessions,
            OperationRegistry operations, ClockPort clock) {
        this.projects = projects;
        this.sessions = sessions;
        this.manageSessions = manageSessions;
        this.operations = operations;
        this.clock = clock;
    }

    public void run(UUID projectId, Mode mode, UUID targetProjectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (projects.count() <= 1) {
            throw new DomainException(ErrorCode.LAST_PROJECT);
        }
        if (mode == Mode.MOVE) {
            if (targetProjectId == null || targetProjectId.equals(projectId)) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED,
                        "Choisissez un autre projet où déplacer les spécifications.");
            }
            if (projects.find(targetProjectId).isEmpty()) {
                throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
            }
        }
        List<UUID> contained = sessions.list().stream().filter(s -> projectId.equals(s.projectId()))
                .map(SessionListing::id).toList();
        if (contained.stream().anyMatch(operations::running)) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS);
        }
        for (UUID id : contained) {
            if (mode == Mode.MOVE) {
                sessions.find(id).ifPresent(s -> MoveSession.move(sessions, s, targetProjectId, clock));
            } else {
                manageSessions.delete(id);
            }
        }
        projects.delete(projectId);
    }
}
