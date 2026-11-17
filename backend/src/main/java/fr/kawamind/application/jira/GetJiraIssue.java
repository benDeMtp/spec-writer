package fr.kawamind.application.jira;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.JiraIssue;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.port.WikiMarkup;
import java.time.Instant;

/** Détail d'un ticket Jira, description convertie en Markdown pour l'affichage (FR-043). */
public class GetJiraIssue {

    public record IssueView(String key, String summary, String descriptionMarkdown, Instant updated, String url) {
    }

    private final IssueTracker tracker;
    private final JiraAccess jira;
    private final WikiMarkup markup;

    public GetJiraIssue(IssueTracker tracker, JiraAccess jira, WikiMarkup markup) {
        this.tracker = tracker;
        this.jira = jira;
        this.markup = markup;
    }

    public IssueView run(String issueKey) {
        JiraAccess.Target target = jira.require();
        JiraIssue issue = read(tracker, target, issueKey);
        String url = target.issueUrl(issue.key());
        return new IssueView(issue.key(), issue.summary(), markup.toMarkdown(issue.description(), url), issue.updated(),
                url);
    }

    /** Lecture d'un ticket, erreurs Jira traduites pour l'utilisateur. */
    static JiraIssue read(IssueTracker tracker, JiraAccess.Target target, String issueKey) {
        if (!LinkedJiraTicket.isKey(issueKey)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Clé de ticket invalide.");
        }
        try {
            return tracker.getIssue(target.endpoint(), issueKey);
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
    }
}
