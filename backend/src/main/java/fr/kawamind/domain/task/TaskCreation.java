package fr.kawamind.domain.task;

/** Une tâche validée est créée dans Jira une par une ; une création interrompue se relance (R10). */
public enum TaskCreation {
    NOT_CREATED,
    CREATED
}
