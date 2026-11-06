package dev.specwriter.application.knowledge;

/**
 * Bilan de l'étape Jira d'une indexation (FR-009 de 003) : tickets lus dans Jira, tickets fermés
 * gardés sans relecture, tickets retirés de l'index. Des nombres seuls, sans clé ni contenu.
 */
public record TicketSummary(int read, int keptClosed, int removed) {

    public TicketSummary {
        if (read < 0 || keptClosed < 0 || removed < 0) {
            throw new IllegalArgumentException("nombre négatif");
        }
    }
}
