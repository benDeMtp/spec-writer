package dev.specwriter.domain.error;

/** Action proposée à l'utilisateur avec un message d'erreur (FR-028). */
public enum ErrorAction {
    RETRY,
    OPEN_SETTINGS,
    /** Réglages du projet courant, sur la clé Jira (012). */
    OPEN_PROJECT_SETTINGS,
    RECONNECT,
    EDIT_INPUT,
    NONE
}
