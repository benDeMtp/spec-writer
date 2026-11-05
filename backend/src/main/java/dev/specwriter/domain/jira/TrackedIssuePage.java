package dev.specwriter.domain.jira;

import java.util.List;

/** Page de tickets pour l'index de connaissance. */
public record TrackedIssuePage(List<TrackedIssue> issues, int startAt, int total) {

    public TrackedIssuePage {
        issues = List.copyOf(issues);
    }

    public boolean last() {
        return issues.isEmpty() || startAt + issues.size() >= total;
    }
}
