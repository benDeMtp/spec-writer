package fr.kawamind.application.settings;

import fr.kawamind.domain.port.ConfluenceSettingsRepository;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.settings.ConfluenceSettings;
import fr.kawamind.domain.settings.SecretPersistence;

/**
 * Enregistre le serveur Confluence (016, R4). Le jeton part au trousseau, jamais en base ; absent,
 * le jeton déjà enregistré est conservé.
 */
public class SaveConfluenceSettings {

    public record Command(String serverUrl, String token, String caCertificatePath, boolean acceptInsecureHttp) {

        @Override
        public String toString() {
            return "Command[serverUrl=" + serverUrl + ", token=" + (token == null ? "absent" : "***") + "]";
        }
    }

    private final ConfluenceSettingsRepository settings;
    private final SecretStore secrets;

    public SaveConfluenceSettings(ConfluenceSettingsRepository settings, SecretStore secrets) {
        this.settings = settings;
        this.secrets = secrets;
    }

    public ConfluenceSettings run(Command command) {
        ConfluenceSettings validated = ConfluenceSettings.create(command.serverUrl(), command.caCertificatePath(),
                command.acceptInsecureHttp(), SecretPersistence.KEYCHAIN);
        if (command.token() != null && !command.token().isBlank()) {
            secrets.put(SecretStore.CONFLUENCE_PAT, command.token().strip());
        }
        ConfluenceSettings saved = validated.withPersistence(SecretPersistence.from(secrets.persistence()));
        settings.save(saved);
        return saved;
    }

    /** Jeton enregistré, sans jamais le rendre. */
    public boolean tokenSet() {
        return secrets.get(SecretStore.CONFLUENCE_PAT).isPresent();
    }
}
