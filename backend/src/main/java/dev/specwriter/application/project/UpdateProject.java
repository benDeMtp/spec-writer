package dev.specwriter.application.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import java.util.UUID;

/** Renomme ou modifie un projet (FR-002). */
public class UpdateProject {

    private final ProjectRepository projects;
    private final ClockPort clock;

    public UpdateProject(ProjectRepository projects, ClockPort clock) {
        this.projects = projects;
        this.clock = clock;
    }

    public Project run(UUID id, String name, String description, String jiraProjectKey) {
        Project existing = projects.find(id).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        Project changed = existing.update(ProjectName.of(name), description, JiraProjectKey.ofNullable(jiraProjectKey),
                clock.now());
        projects.findByNameKey(changed.nameKey()).filter(other -> !other.id().equals(id)).ifPresent(other -> {
            throw new DomainException(ErrorCode.PROJECT_NAME_TAKEN);
        });
        projects.save(changed);
        return changed;
    }
}
