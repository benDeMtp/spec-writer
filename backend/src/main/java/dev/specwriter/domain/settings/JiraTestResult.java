package dev.specwriter.domain.settings;

/** Résultat du test de connexion Jira (FR-032), chacun avec son message et son action. */
public enum JiraTestResult {
    OK("Connexion à Jira réussie."),
    /** 012 : serveur et jeton valides, mais ni clé de projet ni clé par défaut à vérifier. */
    OK_NO_PROJECT("Connexion à Jira réussie. Aucun projet Jira n'est associé au projet courant : renseignez sa clé"
            + " dans les réglages du projet."),
    UNREACHABLE("Le serveur Jira ne répond pas. Vérifiez l'adresse, votre VPN ou le certificat de l'entreprise."),
    TOKEN_REJECTED("Le jeton a été refusé. Créez un nouveau jeton d'accès personnel dans Jira, puis remplacez-le ici."),
    PROJECT_NOT_FOUND("Ce projet est introuvable ou vous n'y avez pas accès. Vérifiez sa clé."),
    MISSING_PERMISSION(
            "Votre compte n'a pas tous les droits nécessaires sur ce projet (consulter, créer et modifier des tickets)."),
    UNSUPPORTED_VERSION("Cette version de Jira est trop ancienne : spec-writer demande Jira 8.14 ou plus récent."),
    CONSENT_REQUIRED("Votre accord est nécessaire avant d'envoyer des données à Jira.");

    private final String userMessage;

    JiraTestResult(String userMessage) {
        this.userMessage = userMessage;
    }

    public String userMessage() {
        return userMessage;
    }
}
