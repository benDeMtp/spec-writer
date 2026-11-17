package fr.kawamind.domain.port;

import fr.kawamind.domain.settings.ConfluenceSettings;
import java.util.Optional;

/** Serveur Confluence de l'outil (016, R4) ; le jeton n'y est jamais. */
public interface ConfluenceSettingsRepository {

    Optional<ConfluenceSettings> find();

    void save(ConfluenceSettings settings);

    void delete();
}
