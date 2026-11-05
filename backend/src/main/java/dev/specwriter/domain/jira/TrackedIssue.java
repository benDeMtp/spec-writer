package dev.specwriter.domain.jira;

import java.time.Instant;
import java.util.List;

/**
 * Ticket lu pour l'index de connaissance (research.md R20) : description déjà convertie en
 * Markdown, statut et sa catégorie Jira ({@code new}, {@code indeterminate}, {@code done}), et ses
 * liens vers d'autres tickets (parent, sous-tâches, liens explicites, research.md R29).
 */
public record TrackedIssue(String key, String summary, String description, String status, String statusCategory,
        String issueType, Instant updated, List<TicketLink> links) {

    public TrackedIssue {
        links = links == null ? List.of() : List.copyOf(links);
    }

    public TrackedIssue(String key, String summary, String description, String status, String statusCategory,
            String issueType, Instant updated) {
        this(key, summary, description, status, statusCategory, issueType, updated, List.of());
    }
}
