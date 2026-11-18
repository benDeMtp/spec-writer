package fr.kawamind.application.settings;

import fr.kawamind.domain.port.ConfluenceSettingsRepository;
import fr.kawamind.domain.port.SecretStore;

/**
 * Retire le serveur Confluence et son jeton du trousseau (016). Les racines des projets restent :
 * leurs pages indexées sont gardées, et leur lecture est en erreur jusqu'à un nouveau serveur.
 */
public class DeleteConfluenceSettings {

    private final ConfluenceSettingsRepository settings;
    private final SecretStore secrets;

    public DeleteConfluenceSettings(ConfluenceSettingsRepository settings, SecretStore secrets) {
        this.settings = settings;
        this.secrets = secrets;
    }

    public void run() {
        secrets.delete(SecretStore.CONFLUENCE_PAT);
        settings.delete();
    }
}
