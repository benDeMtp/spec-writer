package fr.kawamind.domain.jira;

import java.time.Instant;

/**
 * Statut d'un ticket relu à l'affichage d'un ticket proche (FR-034). {@code assignee} : login de la
 * personne affectée, nul si personne (023, US5).
 */
public record IssueStatus(String key, String status, String statusCategory, Instant updated, String assignee) {

    public IssueStatus(String key, String status, String statusCategory, Instant updated) {
        this(key, status, statusCategory, updated, null);
    }

    public boolean closed() {
        return "done".equals(statusCategory);
    }
}
