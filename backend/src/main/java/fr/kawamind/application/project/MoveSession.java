package fr.kawamind.application.project;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.session.Session;
import java.util.UUID;

/** Déplace une spécification vers un autre projet ; ses versions la suivent (FR-004). */
public class MoveSession {

    private final SessionRepository sessions;
    private final ProjectRepository projects;
    private final ClockPort clock;

    public MoveSession(SessionRepository sessions, ProjectRepository projects, ClockPort clock) {
        this.sessions = sessions;
        this.projects = projects;
        this.clock = clock;
    }

    public void run(UUID sessionId, UUID projectId) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        move(sessions, session, projectId, clock);
    }

    static void move(SessionRepository sessions, Session session, UUID projectId, ClockPort clock) {
        sessions.save(session.movedTo(projectId, clock.now()),
                sessions.questionnaire(session.id()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
    }
}
