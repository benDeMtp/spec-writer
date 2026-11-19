package fr.kawamind.support;

import fr.kawamind.domain.port.ConfluenceSettingsRepository;
import fr.kawamind.domain.settings.ConfluenceSettings;
import java.util.Optional;

/** Serveur Confluence en mémoire (016). */
public class InMemoryConfluenceSettings implements ConfluenceSettingsRepository {

    public ConfluenceSettings value;

    @Override
    public Optional<ConfluenceSettings> find() {
        return Optional.ofNullable(value);
    }

    @Override
    public void save(ConfluenceSettings settings) {
        value = settings;
    }

    @Override
    public void delete() {
        value = null;
    }
}
