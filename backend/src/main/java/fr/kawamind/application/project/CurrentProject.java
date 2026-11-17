package fr.kawamind.application.project;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.Project;

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
