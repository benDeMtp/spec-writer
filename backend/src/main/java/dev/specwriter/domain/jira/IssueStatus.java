package dev.specwriter.domain.jira;

import java.time.Instant;

/** Statut d'un ticket relu à l'affichage d'un ticket proche (FR-034). */
public record IssueStatus(String key, String status, String statusCategory, Instant updated) {

    public boolean closed() {
        return "done".equals(statusCategory);
    }
}
