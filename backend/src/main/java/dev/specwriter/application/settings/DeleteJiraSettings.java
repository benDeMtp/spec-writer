package dev.specwriter.application.settings;

import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SettingsRepository;

/** Supprime la configuration Jira et son jeton du trousseau. */
public class DeleteJiraSettings {

    private final SettingsRepository settings;
    private final SecretStore secrets;

    public DeleteJiraSettings(SettingsRepository settings, SecretStore secrets) {
        this.settings = settings;
        this.secrets = secrets;
    }

    public void run() {
        secrets.delete(SecretStore.JIRA_PAT);
        settings.deleteJira();
    }
}
