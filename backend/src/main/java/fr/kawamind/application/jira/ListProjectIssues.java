package fr.kawamind.application.jira;

import fr.kawamind.domain.jira.IssueFilters;
import fr.kawamind.domain.jira.JiraLabel;
import fr.kawamind.domain.port.JiraLabelRepository;
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

    /**
     * {@code label} : label de reconnaissance du projet, nul sans réglage ; {@code labelled} et
     * {@code done} : filtres appliqués (024, contracts/rest-api.md).
     */
    public record IssuePage(String jiraProjectKey, SourceSyncResult lastSync, int total, int startAt,
            List<ListedIssue> issues, String label, boolean labelled, boolean done) {

        public IssuePage {
            issues = List.copyOf(issues);
        }
    }

    private final ProjectRepository projects;
    private final JiraAccess jira;
    private final ListedIssues listed;
    private final SourceSyncRepository syncs;
    /** Label de chaque projet (024) ; sans lui (null), pas de filtre du label. */
    private final JiraLabelRepository labels;

    public ListProjectIssues(ProjectRepository projects, JiraAccess jira, ListedIssues listed,
            SourceSyncRepository syncs) {
        this(projects, jira, listed, syncs, null);
    }

    public ListProjectIssues(ProjectRepository projects, JiraAccess jira, ListedIssues listed,
            SourceSyncRepository syncs, JiraLabelRepository labels) {
        this.labels = labels;
        this.projects = projects;
        this.jira = jira;
        this.listed = listed;
        this.syncs = syncs;
    }

    public IssuePage run(UUID projectId, IssueSort sort, int startAt, int size) {
        return run(projectId, sort, false, true, startAt, size);
    }

    /**
     * {@code labelled} : seulement les tickets de premier niveau qui portent le label du projet (sans
     * effet sans label) ; {@code done} : inclure les tickets de premier niveau terminés (024, R6).
     */
    public IssuePage run(UUID projectId, IssueSort sort, boolean labelled, boolean done, int startAt, int size) {
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
        String label = labels == null ? null : labels.find(projectId).map(JiraLabel::value).orElse(null);
        boolean byLabel = labelled && label != null;
        IssueFilters filters = new IssueFilters(byLabel ? label : null, done);
        return new IssuePage(key, last, listed.count(projectId, filters), startAt,
                listed.page(projectId, sort, filters, startAt, size), label, byLabel, done);
    }
}
