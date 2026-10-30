package dev.specwriter.domain.jira;

import java.time.Instant;

/** Ligne d'un résultat de recherche Jira ({@code fields=summary,status,issuetype,updated}). */
public record JiraIssueSummary(String key, String summary, String status, String issueType, Instant updated) {
}
