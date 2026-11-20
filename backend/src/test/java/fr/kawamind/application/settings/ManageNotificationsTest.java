package fr.kawamind.application.settings;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.support.InMemorySettings;
import org.junit.jupiter.api.Test;

/** T044 : notifications des sources mises à jour, désactivées par défaut (017, FR-016). */
class ManageNotificationsTest {

    @Test
    void desactiveesParDefautPuisActivees() {
        InMemorySettings settings = new InMemorySettings();
        ManageNotifications manage = new ManageNotifications(settings);
        assertThat(manage.get().sourceUpdates()).isFalse();
        assertThat(manage.save(true).sourceUpdates()).isTrue();
        assertThat(settings.notificationsEnabled()).isTrue();
        assertThat(manage.save(false).sourceUpdates()).isFalse();
    }
}
