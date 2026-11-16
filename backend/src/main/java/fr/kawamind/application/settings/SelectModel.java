package fr.kawamind.application.settings;

import fr.kawamind.domain.port.SettingsRepository;
import fr.kawamind.domain.settings.CopilotSettings;

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
