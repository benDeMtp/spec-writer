package dev.specwriter.support;

import dev.specwriter.domain.jira.CreatedIssue;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueType;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.JiraIssuePage;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.jira.JiraSearchPage;
import dev.specwriter.domain.jira.RequiredField;
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
    public final List<IssueType> types = new ArrayList<>(List.of(new IssueType("10002", "Bug"), new IssueType("10001", "Story")));
    public final Map<String, List<RequiredField>> required = new LinkedHashMap<>();
    public final List<Map<String, Object>> created = new ArrayList<>();
    public IssueTrackerException createFailure;
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
    public JiraSearchPage searchText(JiraEndpoint endpoint, String projectKey, String terms, int startAt, int maxResults) {
        calls.add("search " + projectKey + " " + terms);
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

    /** Les tickets de {@link #issues} du projet, du plus récent au plus ancien. */
    @Override
    public JiraIssuePage recentIssues(JiraEndpoint endpoint, String projectKey, int startAt, int maxResults) {
        calls.add("recent " + projectKey + " " + startAt + " " + maxResults);
        fail();
        List<JiraIssue> all = issues.values().stream().filter(i -> i.key().startsWith(projectKey + "-"))
                .sorted(java.util.Comparator.comparing(JiraIssue::updated).reversed()).toList();
        return new JiraIssuePage(all.stream().skip(startAt).limit(maxResults).toList(), startAt, all.size());
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

    @Override
    public List<IssueType> issueTypes(JiraEndpoint endpoint, String projectKey) {
        calls.add("issuetypes " + projectKey);
        fail();
        return types;
    }

    @Override
    public List<RequiredField> requiredFields(JiraEndpoint endpoint, String projectKey, String issueTypeId) {
        calls.add("fields " + projectKey + " " + issueTypeId);
        fail();
        return required.getOrDefault(issueTypeId, List.of());
    }

    @Override
    public CreatedIssue createIssue(JiraEndpoint endpoint, String projectKey, String issueTypeId, String summary,
            String wikiDescription, Map<String, Object> extraFields) {
        calls.add("create " + projectKey + " " + issueTypeId);
        fail();
        if (createFailure != null) {
            throw createFailure;
        }
        Map<String, Object> fields = new LinkedHashMap<>(extraFields);
        fields.put("summary", summary);
        fields.put("description", wikiDescription);
        created.add(fields);
        String key = projectKey + "-" + (100 + created.size());
        writes.add(key);
        issues.put(key, new JiraIssue(key, summary, wikiDescription, java.time.Instant.parse("2026-10-30T12:00:00Z")));
        return new CreatedIssue(String.valueOf(20000 + created.size()), key);
    }
}
