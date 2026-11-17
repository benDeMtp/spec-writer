package fr.kawamind.application.project;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.JiraProjectKey;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;

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
