package dev.specwriter.application.project;

import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.session.Session;

/** Contexte du projet d'une spécification, transmis à chaque opération du LLM (FR-011). */
public final class ProjectContexts {

    private ProjectContexts() {
    }

    public static ProjectContext of(ProjectRepository projects, Session session) {
        return session.projectId() == null ? ProjectContext.NONE
                : projects.find(session.projectId()).map(Project::context).orElse(ProjectContext.NONE);
    }
}
