package dev.specwriter.domain.jira;

import java.util.List;

/** Page de résultats d'une recherche JQL. */
public record JiraSearchPage(List<JiraIssueSummary> issues, int startAt, int total) {

    public JiraSearchPage {
        issues = List.copyOf(issues);
    }
}
