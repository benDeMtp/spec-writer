package fr.kawamind.application.session;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.session.Implementation;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.SpecificationVersion;
import java.util.UUID;

/**
 * Évolution d'une spécification (FR-026b) : une nouvelle spécification du même projet, dont le
 * besoin est le Markdown de la dernière version, en « brouillon », qui cite son origine.
 */
public class EvolveSpecification {

    static final String PREFIX = "Évolution de ";

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final ProjectRepository projects;
    private final StartSession start;

    public EvolveSpecification(SessionRepository sessions, SpecificationRepository versions, ProjectRepository projects,
            StartSession start) {
        this.sessions = sessions;
        this.versions = versions;
        this.projects = projects;
        this.start = start;
    }

    public SessionSnapshot run(UUID originId) {
        Session origin = sessions.find(originId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        int last = versions.lastNumber(originId);
        SpecificationVersion latest = versions.find(originId, last).orElseThrow(() -> new DomainException(
                ErrorCode.VALIDATION_FAILED, "Cette spécification n'a aucune version à faire évoluer."));
        Project project = projects.find(origin.projectId()).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        String need = latest.markdown().length() > Need.MAX_LENGTH ? latest.markdown().substring(0, Need.MAX_LENGTH)
                : latest.markdown();
        String name = PREFIX + origin.name();
        SessionSnapshot evolved = start.run(need, name.length() > Session.MAX_NAME ? name.substring(0, Session.MAX_NAME) : name,
                project);
        sessions.saveImplementation(evolved.session().id(), Implementation.evolvedFrom(originId));
        return evolved;
    }
}
