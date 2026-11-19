package fr.kawamind.application.settings;

import fr.kawamind.domain.port.SettingsRepository;

/**
 * Notifications du navigateur des sources mises à jour (017, US3, FR-016) : désactivées par défaut.
 * La permission du navigateur, elle, est demandée par l'onglet.
 */
public class ManageNotifications {

    public record View(boolean sourceUpdates) {
    }

    private final SettingsRepository settings;

    public ManageNotifications(SettingsRepository settings) {
        this.settings = settings;
    }

    public View get() {
        return new View(settings.notificationsEnabled());
    }

    public View save(boolean sourceUpdates) {
        settings.saveNotificationsEnabled(sourceUpdates);
        return get();
    }
}
