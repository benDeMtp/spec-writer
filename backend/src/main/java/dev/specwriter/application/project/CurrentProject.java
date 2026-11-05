package dev.specwriter.application.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;

/**
 * Projet courant (FR-005) : celui choisi par l'utilisateur, sinon le premier projet par nom. Il en
 * existe toujours au moins un, car le dernier projet ne peut pas être supprimé (FR-002).
 */
public final class CurrentProject {

    private CurrentProject() {
    }

    public static Project resolve(ProjectRepository projects) {
        return projects.currentProjectId().flatMap(projects::find)
                .or(() -> projects.list().stream().findFirst())
                .orElseThrow(() -> new DomainException(ErrorCode.INTERNAL_ERROR));
    }
}
