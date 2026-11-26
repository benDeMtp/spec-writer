package fr.kawamind.domain.jira;

import java.time.Instant;
import java.util.List;

/**
 * Ticket lu pour l'index de connaissance (research.md R20) : description déjà convertie en
 * Markdown, statut et sa catégorie Jira ({@code new}, {@code indeterminate}, {@code done}), et ses
 * liens vers d'autres tickets (parent, sous-tâches, liens explicites, research.md R29), et ses labels
 * (024, research R2).
 */
public record TrackedIssue(String key, String summary, String description, String status, String statusCategory,
        String issueType, Instant updated, List<TicketLink> links, List<String> labels) {

    public TrackedIssue {
        links = links == null ? List.of() : List.copyOf(links);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    public TrackedIssue(String key, String summary, String description, String status, String statusCategory,
            String issueType, Instant updated, List<TicketLink> links) {
        this(key, summary, description, status, statusCategory, issueType, updated, links, List.of());
    }

    /** Le même ticket, avec ces labels. */
    public TrackedIssue withLabels(List<String> newLabels) {
        return new TrackedIssue(key, summary, description, status, statusCategory, issueType, updated, links, newLabels);
    }

    public TrackedIssue(String key, String summary, String description, String status, String statusCategory,
            String issueType, Instant updated) {
        this(key, summary, description, status, statusCategory, issueType, updated, List.of());
    }
}
