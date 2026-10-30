package dev.specwriter.support;

import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.jira.JiraSearchPage;
import dev.specwriter.domain.port.IssueTracker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Jira en mémoire : tickets, journal des appels et des écritures, panne simulée. */
public class FakeIssueTracker implements IssueTracker {

    public final Map<String, JiraIssue> issues = new LinkedHashMap<>();
    public final List<JiraIssueSummary> searchResults = new ArrayList<>();
    public final List<String> calls = new ArrayList<>();
    public final List<String> writes = new ArrayList<>();
    public Kind failure;
    public long searchDelayMillis;

    private void fail() {
        if (failure != null) {
            throw new IssueTrackerException(failure, "simulé");
        }
    }

    @Override
    public String serverVersion(JiraEndpoint endpoint) {
        calls.add("serverInfo");
        fail();
        return "9.12.3";
    }

    @Override
    public String currentUser(JiraEndpoint endpoint) {
        calls.add("myself");
        fail();
        return "ada";
    }

    @Override
    public void project(JiraEndpoint endpoint, String projectKey) {
        calls.add("project");
        fail();
    }

    @Override
    public Map<String, Boolean> permissions(JiraEndpoint endpoint, String projectKey, Set<String> permissions) {
        calls.add("mypermissions");
        fail();
        return Map.of(PERM_BROWSE, true, PERM_CREATE, true, PERM_EDIT, true);
    }

    @Override
    public JiraSearchPage search(JiraEndpoint endpoint, String jql, int startAt, int maxResults) {
        calls.add("search " + jql);
        if (searchDelayMillis > 0) {
            try {
                Thread.sleep(searchDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        fail();
        return new JiraSearchPage(searchResults, startAt, searchResults.size());
    }

    @Override
    public JiraIssue getIssue(JiraEndpoint endpoint, String issueKey) {
        calls.add("get " + issueKey);
        fail();
        JiraIssue issue = issues.get(issueKey);
        if (issue == null) {
            throw new IssueTrackerException(Kind.NOT_FOUND, issueKey);
        }
        return issue;
    }

    @Override
    public void updateDescription(JiraEndpoint endpoint, String issueKey, String wikiDescription) {
        calls.add("put " + issueKey);
        fail();
        writes.add(issueKey);
        JiraIssue old = issues.get(issueKey);
        issues.put(issueKey, new JiraIssue(issueKey, old == null ? issueKey : old.summary(), wikiDescription,
                old == null ? java.time.Instant.EPOCH : old.updated().plusSeconds(1)));
    }
}
