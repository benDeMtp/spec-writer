package dev.specwriter.domain.error;

/**
 * Codes d'erreur stables exposés au front (contracts/openapi.yaml, contracts/llm-port.md).
 * Chaque code porte un statut HTTP, un message métier par défaut et l'action à proposer (FR-028).
 */
public enum ErrorCode {
    // Sécurité locale
    FORBIDDEN(403, "Accès refusé. Rouvrez l'application depuis son lanceur.", ErrorAction.NONE),
    /** 012 (FR-018) : cookie absent ou périmé, ou jeton de lancement refusé ; le front affiche son écran. */
    SESSION_REQUIRED(403, "Cette fenêtre n'est plus reliée à spec-writer. Relancez-la depuis son raccourci.",
            ErrorAction.NONE),
    AGENT_ACCESS_DENIED(401, "Accès des agents refusé : activez-le dans la configuration, ou relancez la passerelle.",
            ErrorAction.NONE),
    PAYLOAD_TOO_LARGE(413, "Le contenu envoyé est trop volumineux. Un document joint fait 20 Mo au plus.",
            ErrorAction.EDIT_INPUT),
    UNSUPPORTED_MEDIA_TYPE(415, "Format de requête non pris en charge.", ErrorAction.NONE),
    NOT_FOUND(404, "Élément introuvable.", ErrorAction.NONE),
    VALIDATION_FAILED(422, "Certaines informations saisies ne sont pas valides.", ErrorAction.EDIT_INPUT),
    INTERNAL_ERROR(500, "Une erreur inattendue est survenue, réessayez.", ErrorAction.RETRY),

    // Besoin et questionnaire
    NEED_TOO_SHORT(422,
            "Décrivez votre besoin en au moins 10 mots. Exemple : « Les employés doivent pouvoir réserver une salle "
                    + "de réunion pour une durée donnée, et être prévenus si elle est déjà prise. »",
            ErrorAction.EDIT_INPUT),
    NEED_TOO_LONG(422, "Le besoin dépasse 10 000 caractères. Raccourcissez-le ou découpez-le en plusieurs sessions.",
            ErrorAction.EDIT_INPUT),
    // Conversation (010)
    CONVERSATION_NOT_FOUND(404, "Cette conversation n'existe plus.", ErrorAction.NONE),
    DISCUSSION_NOT_FOUND(404, "Cette spécification n'a pas encore de discussion.", ErrorAction.NONE),
    NOT_A_DISCUSSION(422, "Cette conversation n'est pas la discussion d'une spécification.", ErrorAction.NONE),
    MESSAGE_TOO_LONG(422, "Un message fait 8 000 caractères au plus. Raccourcissez-le, ou joignez le texte comme document.",
            ErrorAction.EDIT_INPUT),
    NEED_DRAFT_INVALID(422, "Le besoin ou un point établi n'est pas valide : corrigez-le avant de démarrer.",
            ErrorAction.EDIT_INPUT),
    // Documents joints (009)
    UNSUPPORTED_FORMAT(422, "Format non pris en charge : joignez un document Word (.docx), Excel (.xlsx), PDF ou HTML.",
            ErrorAction.EDIT_INPUT),
    DOCUMENT_UNREADABLE(422, "Le document n'a pas pu être lu.", ErrorAction.EDIT_INPUT),
    ATTACHMENT_LIMIT(422, "Une spécification accepte 5 documents joints au plus. Retirez-en un pour en joindre un autre.",
            ErrorAction.EDIT_INPUT),
    ATTACHMENT_DUPLICATE(422, "Ce document est déjà joint (même contenu ou même nom de fichier).", ErrorAction.EDIT_INPUT),
    SESSION_UNREADABLE(422, "Cette session est illisible : sa sauvegarde est endommagée. Vous pouvez la supprimer.",
            ErrorAction.NONE),
    CONTRADICTION_UNRESOLVED(422, "Une contradiction doit être tranchée avant de générer la spécification.",
            ErrorAction.EDIT_INPUT),

    // Consentement et services
    CONSENT_REQUIRED(428, "Votre accord est nécessaire avant d'envoyer des données à ce service.",
            ErrorAction.OPEN_SETTINGS),
    COPILOT_NOT_CONNECTED(503, "GitHub Copilot n'est pas connecté. Connectez-vous depuis la configuration.",
            ErrorAction.OPEN_SETTINGS),
    LLM_UNAVAILABLE(503, "GitHub Copilot ne répond pas. Vos réponses sont conservées, réessayez dans un instant.",
            ErrorAction.RETRY),
    COPILOT_CLI_MISSING(503, "Le Copilot CLI de GitHub est introuvable. Installez-le (voir le README), puis relancez "
            + "spec-writer.", ErrorAction.NONE),
    COPILOT_CLI_OUTDATED(503, "Le Copilot CLI installé est trop ancien. Lancez « copilot update », puis relancez "
            + "spec-writer.", ErrorAction.NONE),
    COPILOT_KEYCHAIN_DENIED(409, "Le trousseau de votre poste refuse de supprimer le jeton GitHub : il a été rangé par "
            + "un autre programme (une autre installation du Copilot CLI). Lancez « copilot logout » dans un terminal, "
            + "ou supprimez l'élément « copilot-cli » dans Trousseaux d'accès, puis réessayez.", ErrorAction.NONE),
    LLM_INVALID_OUTPUT(502, "La génération n'a pas produit une spécification valide. Réessayez.", ErrorAction.RETRY),
    MODEL_UNAVAILABLE(422, "Le modèle choisi n'est plus disponible pour votre compte. Choisissez-en un autre.",
            ErrorAction.OPEN_SETTINGS),
    QUOTA_EXHAUSTED(429, "Le quota GitHub Copilot de votre compte est épuisé.", ErrorAction.NONE),

    // Projets (feature 002)
    PROJECT_NAME_TAKEN(409, "Un projet porte déjà ce nom.", ErrorAction.EDIT_INPUT),
    PROJECT_NOT_FOUND(404, "Ce projet n'existe plus.", ErrorAction.NONE),
    LAST_PROJECT(409, "Le dernier projet restant ne peut pas être supprimé.", ErrorAction.NONE),
    OPERATION_IN_PROGRESS(409, "Une opération de l'IA est en cours. Attendez sa fin, puis réessayez.", ErrorAction.RETRY),
    SPECIFICATION_LOCKED(409,
            "Cette spécification est implémentée : elle est verrouillée. Rétablissez un statut antérieur, ou créez une "
                    + "évolution.", ErrorAction.NONE),
    UNLOCK_CONFIRMATION_REQUIRED(409, "Confirmez la sortie du statut « implémentée ».", ErrorAction.NONE),
    TRANSITION_NOT_ALLOWED(409, "Ce changement de statut n'est pas permis.", ErrorAction.NONE),
    CONTEXT_CHANGED(409, "Le contexte du projet a changé depuis cette proposition. Régénérez-la ou gardez la fiche actuelle.",
            ErrorAction.NONE),

    // Jira
    JIRA_NOT_CONFIGURED(409, "Jira n'est pas configuré.", ErrorAction.OPEN_SETTINGS),
    JIRA_PROJECT_MISSING(409,
            "Aucun projet Jira n'est associé à ce projet. Renseignez sa clé Jira dans les réglages du projet.",
            ErrorAction.OPEN_PROJECT_SETTINGS),
    JIRA_UNREACHABLE(503, "Le serveur Jira ne répond pas. Vérifiez l'adresse ou votre connexion.", ErrorAction.RETRY),
    JIRA_TOKEN_REJECTED(401, "Le jeton Jira a été refusé. Remplacez-le dans la configuration.",
            ErrorAction.OPEN_SETTINGS),
    JIRA_PERMISSION_DENIED(403, "Votre compte Jira n'a pas les droits nécessaires sur ce projet.", ErrorAction.NONE),
    JIRA_REJECTED(502, "Jira a refusé l'opération.", ErrorAction.EDIT_INPUT),
    JIRA_CONFLICT(409, "Le ticket a été modifié dans Jira depuis votre dernière lecture.", ErrorAction.NONE),
    IMPORT_UNSTRUCTURABLE(502, "Le contenu du ticket n'a pas pu être structuré en spécification.", ErrorAction.RETRY),
    ALREADY_LINKED(409, "Cette session est déjà liée à un ticket Jira.", ErrorAction.NONE),

    // Secrets
    KEYCHAIN_UNAVAILABLE(503,
            "Le trousseau du système n'est pas accessible. Le jeton sera gardé en mémoire jusqu'à la fermeture.",
            ErrorAction.NONE);

    private final int status;
    private final String defaultMessage;
    private final ErrorAction action;

    ErrorCode(int status, String defaultMessage, ErrorAction action) {
        this.status = status;
        this.defaultMessage = defaultMessage;
        this.action = action;
    }

    public int status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public ErrorAction action() {
        return action;
    }
}
