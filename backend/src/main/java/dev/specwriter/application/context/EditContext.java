package dev.specwriter.application.context;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.ContextSheet;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import java.util.UUID;

/** Enregistre une fiche modifiée à la main (FR-008a, FR-013c) ; son origine devient EDITED. */
public class EditContext {

    private final ProjectRepository projects;
    private final ClockPort clock;

    public EditContext(ProjectRepository projects, ClockPort clock) {
        this.projects = projects;
        this.clock = clock;
    }

    public Project run(UUID projectId, ContextSheet sheet, String markdown) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        Project edited = project.withContext(sheet, ProjectContext.edited(markdown, clock.now()));
        projects.save(edited);
        return edited;
    }
}
