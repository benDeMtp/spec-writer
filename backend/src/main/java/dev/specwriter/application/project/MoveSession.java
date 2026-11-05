package dev.specwriter.application.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.session.Session;
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
