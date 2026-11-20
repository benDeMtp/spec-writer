package fr.kawamind.application.jira;

import fr.kawamind.application.knowledge.IndexationScope;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueSort;
import fr.kawamind.domain.jira.ListedIssue;
import fr.kawamind.domain.port.ListedIssues;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.sync.SourceSync;
import fr.kawamind.domain.sync.SourceSyncResult;
import java.util.List;
import java.util.UUID;

/**
 * Tickets du projet Jira d'un projet, lus dans l'index local, triés et paginés par la base (017,
 * US1, research.md R1). Aucun appel à Jira : la fraîcheur est la dernière synchronisation de Jira.
 */
public class ListProjectIssues {

    public static final int MAX_SIZE = 100;

    public record IssuePage(String jiraProjectKey, SourceSyncResult lastSync, int total, int startAt,
            List<ListedIssue> issues) {

        public IssuePage {
            issues = List.copyOf(issues);
        }
    }

    private final ProjectRepository projects;
    private final JiraAccess jira;
    private final ListedIssues listed;
    private final SourceSyncRepository syncs;

    public ListProjectIssues(ProjectRepository projects, JiraAccess jira, ListedIssues listed,
            SourceSyncRepository syncs) {
        this.projects = projects;
        this.jira = jira;
        this.listed = listed;
        this.syncs = syncs;
    }

    public IssuePage run(UUID projectId, IssueSort sort, int startAt, int size) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        if (startAt < 0 || size < 1 || size > MAX_SIZE) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "La position doit être positive et la page compter de 1 à " + MAX_SIZE + " tickets.");
        }
        String key = jira.configured() ? jira.effectiveProject(project) : null;
        if (key == null) {
            throw new DomainException(ErrorCode.JIRA_NOT_CONFIGURED);
        }
        SourceSyncResult last = syncs.find(projectId, IndexationScope.JIRA).map(SourceSync::last).orElse(null);
        return new IssuePage(key, last, listed.count(projectId), startAt, listed.page(projectId, sort, startAt, size));
    }
}
