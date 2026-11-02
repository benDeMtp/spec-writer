package dev.specwriter.domain.session;

/** Origine d'une session : saisie locale ou import d'un ticket Jira (FR-046). */
public enum SessionOrigin {
    LOCAL,
    JIRA_IMPORT
}
