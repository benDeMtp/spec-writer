package fr.kawamind.application.settings;

import fr.kawamind.domain.port.GitHubAuthenticator;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.port.SettingsRepository;

/** Déconnexion GitHub : le runtime oublie le compte et les jetons sont supprimés (FR-030b). */
public class DisconnectCopilot {

    private final GitHubAuthenticator authenticator;
    private final SettingsRepository settings;
    private final SecretStore secrets;

    public DisconnectCopilot(GitHubAuthenticator authenticator, SettingsRepository settings, SecretStore secrets) {
        this.authenticator = authenticator;
        this.settings = settings;
        this.secrets = secrets;
    }

    public void run() {
        authenticator.disconnect();
        secrets.delete(SecretStore.COPILOT_ACCESS);
        secrets.delete(SecretStore.COPILOT_REFRESH);
        settings.saveCopilot(settings.copilot().disconnected());
    }
}
