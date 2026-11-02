package dev.specwriter.domain.jira;

import java.time.Instant;

/** Ticket Jira lu pour l'affichage, l'import ou la détection de conflit. {@code description} est en wiki markup. */
public record JiraIssue(String key, String summary, String description, Instant updated) {
}
