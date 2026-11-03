package dev.specwriter.application.session;

import dev.specwriter.application.project.CurrentProject;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionListing;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.List;
import java.util.UUID;

/**
 * Lister les sessions du projet courant (FR-005 de 002), renommer et supprimer des sessions, lister
 * leurs versions (FR-017, FR-018).
 */
public class ManageSessions {

    public record Entry(SessionListing session, int latestVersion) {
    }

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final ProjectRepository projects;
    private final ClockPort clock;

    public ManageSessions(SessionRepository sessions, SpecificationRepository versions, ProjectRepository projects,
            ClockPort clock) {
        this.sessions = sessions;
        this.versions = versions;
        this.projects = projects;
        this.clock = clock;
    }

    /** Sessions du projet courant, les plus récemment modifiées d'abord. */
    public List<Entry> list() {
        UUID current = CurrentProject.resolve(projects).id();
        return sessions.list().stream()
                .filter(s -> current.equals(s.projectId()))
                .map(s -> new Entry(s, s.readable() ? versions.lastNumber(s.id()) : 0))
                .toList();
    }

    public Session rename(UUID id, String name) {
        Session session = existing(id).rename(name, clock.now());
        sessions.save(session, sessions.questionnaire(id).orElseThrow());
        return session;
    }

    /** Suppression physique de la session, de ses questions, réponses et versions. */
    public void delete(UUID id) {
        if (sessions.list().stream().noneMatch(s -> s.id().equals(id))) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        versions.deleteAll(id);
        sessions.delete(id);
    }

    public List<SpecificationVersion> versions(UUID id) {
        existing(id);
        return versions.list(id);
    }

    private Session existing(UUID id) {
        return sessions.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}
