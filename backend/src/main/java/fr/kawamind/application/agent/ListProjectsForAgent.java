package fr.kawamind.application.agent;

import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.JiraProjectKey;
import fr.kawamind.domain.project.Project;
import java.util.List;
import java.util.UUID;

/** Outil MCP {@code list_projects} (FR-025) : ni secret ni configuration (FR-029). */
public class ListProjectsForAgent {

    public record ProjectForAgent(UUID id, String name, String description, String jiraProjectKey) {
    }

    private final ProjectRepository projects;
    private final AgentJournal journal;

    public ListProjectsForAgent(ProjectRepository projects, AgentJournal journal) {
        this.projects = projects;
        this.journal = journal;
    }

    public List<ProjectForAgent> run(String agentName) {
        journal.record(agentName, AgentOperation.LIST_PROJECTS, null, null);
        return projects.list().stream().map(ListProjectsForAgent::view).toList();
    }

    private static ProjectForAgent view(Project p) {
        JiraProjectKey key = p.jiraProjectKey();
        return new ProjectForAgent(p.id(), p.name().value(), p.description(), key == null ? null : key.value());
    }
}
