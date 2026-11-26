package fr.kawamind.domain.port;

import fr.kawamind.domain.jira.IssueFilters;
import fr.kawamind.domain.jira.IssueSort;
import fr.kawamind.domain.jira.ListedIssue;
import java.util.List;
import java.util.UUID;

/** Tickets indexés d'un projet, triés et paginés par la base (017, research.md R1). */
public interface ListedIssues {

    List<ListedIssue> page(UUID projectId, IssueSort sort, int offset, int limit);

    int count(UUID projectId);

    /** Avec les filtres de la vue (024) ; sans filtre par défaut. */
    default List<ListedIssue> page(UUID projectId, IssueSort sort, IssueFilters filters, int offset, int limit) {
        return page(projectId, sort, offset, limit);
    }

    default int count(UUID projectId, IssueFilters filters) {
        return count(projectId);
    }
}
