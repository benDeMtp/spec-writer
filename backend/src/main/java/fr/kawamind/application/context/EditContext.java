package fr.kawamind.application.context;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectContext;
import java.util.UUID;

/** Enregistre la fiche générale, rédigée à la main (FR-008, research.md R25). */
public class EditContext {

    private final ProjectRepository projects;
    private final ClockPort clock;

    public EditContext(ProjectRepository projects, ClockPort clock) {
        this.projects = projects;
        this.clock = clock;
    }

    public Project run(UUID projectId, String markdown) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        Project edited = project.withContext(ProjectContext.edited(markdown, clock.now()));
        projects.save(edited);
        return edited;
    }
}
