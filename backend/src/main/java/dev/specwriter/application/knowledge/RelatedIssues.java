package dev.specwriter.application.knowledge;

import java.time.Instant;
import java.util.List;

/**
 * Tickets proches d'un besoin, 5 au plus, et le choix de l'utilisateur (data-model.md,
 * RelatedIssues ; FR-034, FR-035). Gardés en mémoire, par session.
 */
public record RelatedIssues(Status status, List<Issue> issues, Decision decision) {

    public enum Status { PENDING, READY, PARTIAL, UNAVAILABLE }

    public enum Decision { LINK_NEW, SAME, DIFFERENT, NONE }

    /**
     * Ticket proche : statut relu dans Jira ({@code statusFresh}), sinon statut indexé et sa date ;
     * {@code reason} absente sans Copilot ; {@code parentKey} quand le ticket est une sous-tâche (R29).
     */
    public record Issue(String key, String title, String status, String statusCategory, boolean closed,
            boolean statusFresh, Instant updatedAt, String reason, String url, String parentKey) {

        public Issue(String key, String title, String status, String statusCategory, boolean closed,
                boolean statusFresh, Instant updatedAt, String reason, String url) {
            this(key, title, status, statusCategory, closed, statusFresh, updatedAt, reason, url, null);
        }
    }

    public static final RelatedIssues UNAVAILABLE = new RelatedIssues(Status.UNAVAILABLE, List.of(), null);

    public RelatedIssues {
        issues = List.copyOf(issues);
    }

    /** Des tickets attendent le choix de l'utilisateur : les questions viennent après. */
    public boolean awaitingDecision() {
        return decision == null && !issues.isEmpty();
    }

    public RelatedIssues decided(Decision newDecision) {
        return new RelatedIssues(status, issues, newDecision);
    }
}
