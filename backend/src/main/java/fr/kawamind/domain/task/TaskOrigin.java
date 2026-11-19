package fr.kawamind.domain.task;

/** D'où vient une tâche : proposée par le modèle, ajoutée à la main, ou sous-tâche déjà présente dans Jira. */
public enum TaskOrigin {
    PROPOSED,
    MANUAL,
    JIRA
}
