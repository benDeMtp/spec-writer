package fr.kawamind.infrastructure.github;

import fr.kawamind.domain.port.ModelCapacities;
import fr.kawamind.domain.port.SettingsRepository;
import fr.kawamind.domain.settings.ModelCapacity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.OptionalInt;

/**
 * Capacités lues dans les réglages Copilot (008) : aucun appel à Copilot par génération. Modèle
 * non choisi (défaut du compte) ou absent des capacités : limite inconnue.
 */
@ApplicationScoped
public class SettingsModelCapacities implements ModelCapacities {

    private final SettingsRepository settings;

    SettingsModelCapacities(SettingsRepository settings) {
        this.settings = settings;
    }

    @Override
    public OptionalInt promptTokens() {
        return settings.copilot().capacity().map(ModelCapacity::prompt).orElse(OptionalInt.empty());
    }

    @Override
    public List<ModelCapacity> all() {
        return settings.copilot().capacities();
    }
}
