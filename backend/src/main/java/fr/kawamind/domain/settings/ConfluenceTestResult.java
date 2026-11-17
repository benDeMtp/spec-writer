package fr.kawamind.domain.settings;

/** Résultat du test de connexion à Confluence (016, contracts/rest-api.md), avec son message. */
public enum ConfluenceTestResult {
    OK("Connexion à Confluence réussie."),
    UNREACHABLE("Le serveur Confluence ne répond pas. Vérifiez l'adresse, votre VPN ou le proxy de l'entreprise."),
    UNAUTHORIZED("Le jeton a été refusé. Créez un nouveau jeton d'accès personnel dans Confluence, puis remplacez-le"
            + " ici."),
    TLS_ERROR("Le certificat du serveur n'est pas reconnu. Indiquez le certificat de l'autorité de l'entreprise"
            + " (fichier PEM)."),
    NOT_CONFLUENCE("Cette adresse ne répond pas comme un serveur Confluence. Vérifiez-la, chemin compris"
            + " (par exemple https://wiki.mon-entreprise.fr/confluence).");

    private final String userMessage;

    ConfluenceTestResult(String userMessage) {
        this.userMessage = userMessage;
    }

    public String userMessage() {
        return userMessage;
    }
}
