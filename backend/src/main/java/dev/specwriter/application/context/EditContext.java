package dev.specwriter.application.context;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import java.util.UUID;

/** Enregistre la fiche de contexte modifiée à la main (FR-013c) ; son origine devient EDITED. */
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
