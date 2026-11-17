package fr.kawamind.domain.jira;

/**
 * Transition offerte par le workflow Jira d'un ticket ({@code issue/{key}/transitions}) : son
 * identifiant, son nom et la catégorie du statut d'arrivée ({@code new}, {@code indeterminate},
 * {@code done}), sur laquelle spec-writer choisit (015, research R3).
 */
public record Transition(String id, String name, String toCategory) {
}
