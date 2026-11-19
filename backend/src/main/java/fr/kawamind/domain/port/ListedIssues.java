package fr.kawamind.domain.port;

import fr.kawamind.domain.jira.IssueSort;
import fr.kawamind.domain.jira.ListedIssue;
import java.util.List;
import java.util.UUID;

/** Tickets indexés d'un projet, triés et paginés par la base (017, research.md R1). */
public interface ListedIssues {

    List<ListedIssue> page(UUID projectId, IssueSort sort, int offset, int limit);

    int count(UUID projectId);
}
