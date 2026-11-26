package fr.kawamind.domain.jira;

/**
 * Filtres de la vue « Tickets Jira » sur le premier niveau (024, US2, research R6) : {@code label}
 * garde les tickets qui le portent (nul : pas de filtre) ; {@code done} garde les tickets terminés.
 */
public record IssueFilters(String label, boolean done) {

    /** Aucun filtre : tous les tickets de premier niveau. */
    public static final IssueFilters ALL = new IssueFilters(null, true);
}
