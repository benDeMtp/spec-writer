package dev.specwriter.domain.jira;

import java.util.List;

/** Page de tickets avec leur description, et le total que Jira annonce pour la requête. */
public record JiraIssuePage(List<JiraIssue> issues, int startAt, int total) {

    public JiraIssuePage {
        issues = List.copyOf(issues);
    }
}
