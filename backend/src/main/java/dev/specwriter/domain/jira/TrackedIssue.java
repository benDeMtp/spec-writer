package dev.specwriter.domain.jira;

import java.time.Instant;

/**
 * Ticket lu pour l'index de connaissance (research.md R20) : description déjà convertie en
 * Markdown, statut et sa catégorie Jira ({@code new}, {@code indeterminate}, {@code done}).
 */
public record TrackedIssue(String key, String summary, String description, String status, String statusCategory,
        String issueType, Instant updated) {
}
