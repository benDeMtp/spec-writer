package fr.kawamind.domain.task;

/** Sur quoi une tâche s'appuie : un module touché (013), un cas d'usage, ou rien (sous-tâche reprise de Jira). */
public enum BasisKind {
    MODULE,
    USE_CASE,
    NONE
}
