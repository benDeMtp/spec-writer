package fr.kawamind.domain.task;

/**
 * Statut Jira connu d'un projet (023, research R2), lu dans l'index de ses tickets ; {@code seen} faux
 * pour un statut réglé qui n'y figure plus, dont la catégorie est alors inconnue (nulle).
 */
public record KnownStatus(String name, String category, boolean seen) {
}
