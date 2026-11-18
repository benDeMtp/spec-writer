package fr.kawamind.application.settings;

import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.ConfluenceSettingsRepository;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.settings.ConfluenceSettings;
import java.util.Optional;

/** Serveur Confluence déclaré et son jeton (016, R4) ; vide sans serveur ou sans jeton. */
public class ConfluenceAccess {

    private final ConfluenceSettingsRepository settings;
    private final SecretStore secrets;

    public ConfluenceAccess(ConfluenceSettingsRepository settings, SecretStore secrets) {
        this.settings = settings;
        this.secrets = secrets;
    }

    public Optional<ConfluenceSettings> settings() {
        return settings.find();
    }

    public Optional<ConfluenceClient.Endpoint> endpoint() {
        return settings.find().flatMap(s -> secrets.get(SecretStore.CONFLUENCE_PAT)
                .map(t -> new ConfluenceClient.Endpoint(s.serverUrl(), t, s.caCertificatePath())));
    }
}
