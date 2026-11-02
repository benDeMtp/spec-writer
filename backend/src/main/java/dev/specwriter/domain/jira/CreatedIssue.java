package dev.specwriter.domain.jira;

/** Ticket créé par Jira ({@code POST /issue}). */
public record CreatedIssue(String id, String key) {
}
