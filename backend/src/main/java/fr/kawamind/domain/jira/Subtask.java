package fr.kawamind.domain.jira;

/** Sous-tâche d'une story lue dans Jira (015, research R2, R10) : clé, résumé, statut et sa catégorie. */
public record Subtask(String key, String summary, String status, String statusCategory) {
}
