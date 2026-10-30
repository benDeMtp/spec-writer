package dev.specwriter.application.settings;

import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.CopilotSettings;

/** Choix du modèle parmi ceux autorisés pour le compte (FR-030c). */
public class SelectModel {

    private final SettingsRepository settings;

    public SelectModel(SettingsRepository settings) {
        this.settings = settings;
    }

    public CopilotSettings run(String model) {
        CopilotSettings updated = settings.copilot().withModel(model);
        settings.saveCopilot(updated);
        return updated;
    }
}
