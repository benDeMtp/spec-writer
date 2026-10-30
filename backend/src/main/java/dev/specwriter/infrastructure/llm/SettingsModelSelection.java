package dev.specwriter.infrastructure.llm;

import dev.specwriter.domain.port.SettingsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Modèle choisi par l'utilisateur (FR-030c) ; à défaut, celui de la configuration. */
@ApplicationScoped
public class SettingsModelSelection implements ModelSelection {

    private final SettingsRepository settings;
    private final Optional<String> configured;

    SettingsModelSelection(SettingsRepository settings,
            @ConfigProperty(name = "specwriter.copilot.model") Optional<String> configured) {
        this.settings = settings;
        this.configured = configured;
    }

    @Override
    public Optional<String> currentModel() {
        return Optional.ofNullable(settings.copilot().model())
                .or(() -> configured.filter(m -> !m.isBlank()));
    }
}
