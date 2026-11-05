package dev.specwriter.application.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ProjectRepository;
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
