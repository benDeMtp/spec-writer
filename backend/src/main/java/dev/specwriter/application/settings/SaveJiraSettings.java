package dev.specwriter.application.settings;

import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.JiraSettings;
import dev.specwriter.domain.settings.SecretPersistence;

/**
 * Enregistre l'accès Jira (FR-031). Le jeton part au trousseau, jamais en base (FR-033a) ; absent,
 * le jeton déjà enregistré est conservé.
 */
public class SaveJiraSettings {

    public record Command(String serverUrl, String defaultProjectKey, String token, String caCertificatePath,
            boolean acceptInsecureHttp) {

        @Override
        public String toString() {
            return "Command[serverUrl=" + serverUrl + ", defaultProjectKey=" + defaultProjectKey + ", token="
                    + (token == null ? "absent" : "***") + "]";
        }
    }

    private final SettingsRepository settings;
    private final SecretStore secrets;

    public SaveJiraSettings(SettingsRepository settings, SecretStore secrets) {
        this.settings = settings;
        this.secrets = secrets;
    }

    public JiraSettings run(Command command) {
        JiraSettings validated = JiraSettings.create(command.serverUrl(), command.defaultProjectKey(),
                command.caCertificatePath(), command.acceptInsecureHttp(), SecretPersistence.KEYCHAIN);
        if (command.token() != null && !command.token().isBlank()) {
            secrets.put(SecretStore.JIRA_PAT, command.token().strip());
        }
        JiraSettings saved = validated.withPersistence(SecretPersistence.from(secrets.persistence()));
        settings.saveJira(saved);
        return saved;
    }
}
