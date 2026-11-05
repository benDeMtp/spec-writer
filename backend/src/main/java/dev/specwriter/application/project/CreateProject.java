package dev.specwriter.application.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;

/** Crée un projet ; son nom est unique sans tenir compte de la casse (FR-001). */
public class CreateProject {

    private final ProjectRepository projects;
    private final ClockPort clock;
    private final IdGenerator ids;

    public CreateProject(ProjectRepository projects, ClockPort clock, IdGenerator ids) {
        this.projects = projects;
        this.clock = clock;
        this.ids = ids;
    }

    public Project run(String name, String description, String jiraProjectKey) {
        ProjectName projectName = ProjectName.of(name);
        Project project = Project.create(ids.next(), projectName, description, JiraProjectKey.ofNullable(jiraProjectKey),
                clock.now());
        if (projects.findByNameKey(project.nameKey()).isPresent()) {
            throw new DomainException(ErrorCode.PROJECT_NAME_TAKEN);
        }
        projects.save(project);
        return project;
    }
}
