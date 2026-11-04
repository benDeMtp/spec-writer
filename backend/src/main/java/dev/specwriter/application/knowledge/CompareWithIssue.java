package dev.specwriter.application.knowledge;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.llm.CompareIssueRequest;
import dev.specwriter.domain.llm.IssueComparison;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.session.Session;
import java.util.UUID;

/** Compare le besoin et un ticket proche non fermé : commun, écarts, suggestion (FR-035, research.md R23). */
public class CompareWithIssue {

    private final SessionRepository sessions;
    private final RelatedIssuesStore related;
    private final JiraAccess jira;
    private final IssueTracker tracker;
    private final WikiMarkup markup;
    private final LlmClient llm;
    private final ConsentPolicy consent;

    public CompareWithIssue(SessionRepository sessions, RelatedIssuesStore related, JiraAccess jira, IssueTracker tracker,
            WikiMarkup markup, LlmClient llm, ConsentPolicy consent) {
        this.sessions = sessions;
        this.related = related;
        this.jira = jira;
        this.tracker = tracker;
        this.markup = markup;
        this.llm = llm;
        this.consent = consent;
    }

    public IssueComparison run(UUID sessionId, String key) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        RelatedIssues.Issue issue = related.find(sessionId).flatMap(r -> r.issues().stream()
                .filter(i -> i.key().equals(key)).findFirst())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Ce ticket n'est pas parmi les tickets proches."));
        consent.requireGranted(ConsentService.COPILOT);
        JiraAccess.Target target = jira.require(sessionId);
        JiraIssue read;
        try {
            read = tracker.getIssue(target.endpoint(), key);
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
        String description = markup.toMarkdown(read.description(), target.issueUrl(key));
        return llm.compareWithIssue(new CompareIssueRequest(session.language(), session.need().text(), key,
                read.summary(), description, issue.status()));
    }
}
