package dev.specwriter.application.project;

import dev.specwriter.domain.project.ContextSheets;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;

/** Fiches du projet d'une spécification, transmises à chaque opération du LLM (FR-008a, FR-009). */
public final class ProjectContexts {

    private ProjectContexts() {
    }

    public static ContextSheets of(ProjectRepository projects, Session session) {
        return session.projectId() == null ? ContextSheets.NONE
                : projects.find(session.projectId()).map(Project::contexts).orElse(ContextSheets.NONE);
    }
}
