package dev.specwriter.application.context;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.JiraIssuePage;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tickets du projet Jira associé, pris en compte par la fiche de contexte : les 200 plus récemment
 * modifiés, lus par pages de 50 (FR-013e). Sans Jira configuré ou autorisé, il n'y en a aucun.
 */
final class ProjectTickets {

    static final int MAX = 200;
    static final int PAGE = 50;

    /** Tickets lus, ou ce qui n'a pas pu l'être. */
    record Read(List<JiraIssue> issues, Optional<Unread> unread, JiraAccess.Target target) {
    }

    private ProjectTickets() {
    }

    static Optional<JiraAccess.Target> target(JiraAccess jira, Project project) {
        if (!jira.configured() || !jira.consented()) {
            return Optional.empty();
        }
        String key = jira.effectiveProject(project);
        return key == null ? Optional.empty() : jira.target().map(t -> new JiraAccess.Target(t.endpoint(), key));
    }

    /** Nombre de tickets qui seraient lus, sans lire leur contenu (FR-013b). */
    static int count(JiraAccess jira, IssueTracker tracker, Project project) {
        return target(jira, project).map(t -> {
            try {
                return Math.min(MAX, tracker.recentIssues(t.endpoint(), t.projectKey(), 0, 1).total());
            } catch (IssueTrackerException e) {
                return 0;
            }
        }).orElse(0);
    }

    static Read read(JiraAccess jira, IssueTracker tracker, Project project) {
        Optional<JiraAccess.Target> target = target(jira, project);
        if (target.isEmpty()) {
            return new Read(List.of(), Optional.empty(), null);
        }
        JiraAccess.Target t = target.get();
        List<JiraIssue> issues = new ArrayList<>();
        try {
            for (int start = 0; start < MAX; start += PAGE) {
                JiraIssuePage page = tracker.recentIssues(t.endpoint(), t.projectKey(), start, PAGE);
                issues.addAll(page.issues());
                if (page.issues().size() < PAGE || start + PAGE >= page.total()) {
                    break;
                }
            }
            return new Read(issues.stream().limit(MAX).toList(), Optional.empty(), t);
        } catch (IssueTrackerException e) {
            return new Read(issues, Optional.of(new Unread("Jira " + t.projectKey(), UnreadReason.UNREACHABLE)), t);
        }
    }
}
