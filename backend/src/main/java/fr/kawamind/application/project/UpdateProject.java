package fr.kawamind.application.project;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.JiraProjectKey;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
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
