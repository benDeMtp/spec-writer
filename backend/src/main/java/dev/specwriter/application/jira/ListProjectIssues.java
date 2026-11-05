package dev.specwriter.application.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraSearchPage;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.project.JiraProjectKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tickets d'un projet Jira, du plus récemment modifié au plus ancien, par 25 (FR-020 à FR-022 de
 * 002). Une erreur Jira est remontée telle quelle : l'écran garde la page déjà affichée.
 */
public class ListProjectIssues {

    public static final int PAGE_SIZE = 25;

    public record Issue(String key, String summary, Instant updated, UUID linkedSessionId) {
    }

    public record IssuePage(int startAt, int total, List<Issue> issues) {

        public IssuePage {
            issues = List.copyOf(issues);
        }
    }

    private final IssueTracker tracker;
    private final JiraAccess jira;
    private final LinkedTicketRepository tickets;

    public ListProjectIssues(IssueTracker tracker, JiraAccess jira, LinkedTicketRepository tickets) {
        this.tracker = tracker;
        this.jira = jira;
        this.tickets = tickets;
    }

    public IssuePage run(String projectKey, int startAt) {
        String key = new JiraProjectKey(projectKey).value();
        if (startAt < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La position de la page doit être positive.");
        }
        JiraAccess.Target target = jira.require();
        JiraSearchPage page;
        try {
            page = tracker.projectIssues(target.endpoint(), key, startAt, PAGE_SIZE);
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
        Map<String, UUID> linked = tickets.all().stream()
                .collect(Collectors.toMap(LinkedJiraTicket::issueKey, LinkedJiraTicket::sessionId, (a, b) -> a));
        return new IssuePage(startAt, page.total(), page.issues().stream()
                .map(i -> new Issue(i.key(), i.summary(), i.updated(), linked.get(i.key()))).toList());
    }
}
