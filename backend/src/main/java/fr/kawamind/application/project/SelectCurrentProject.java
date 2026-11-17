package fr.kawamind.application.project;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ProjectRepository;
import java.util.UUID;

/** Change le projet courant, conservé d'un lancement à l'autre (FR-005). */
public class SelectCurrentProject {

    private final ProjectRepository projects;

    public SelectCurrentProject(ProjectRepository projects) {
        this.projects = projects;
    }

    public void run(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        projects.saveCurrentProjectId(projectId);
    }
}
