package dev.specwriter.domain.jira;

/** Type de ticket disponible dans un projet Jira (FR-036a). */
public record IssueType(String id, String name) {

    public static final String STORY = "Story";
}
