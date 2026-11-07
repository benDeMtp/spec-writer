package dev.specwriter.infrastructure.platform;

import dev.specwriter.application.settings.RefreshModelCapacities;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Relit au démarrage, en tâche de fond, la capacité des modèles du compte connecté (008) : le
 * lancement n'attend pas Copilot, et un échec laisse les capacités connues.
 */
@Singleton
public class ModelCapacitiesStartup {

    private static final Logger LOG = Logger.getLogger(ModelCapacitiesStartup.class);

    private final RefreshModelCapacities refresh;

    @Inject
    public ModelCapacitiesStartup(RefreshModelCapacities refresh) {
        this.refresh = refresh;
    }

    void onStart(@Observes StartupEvent event) {
        start();
    }

    Thread start() {
        return Thread.ofVirtual().name("model-capacities").start(() -> {
            try {
                refresh.run();
            } catch (RuntimeException e) {
                LOG.warnf("Capacités des modèles non relues au démarrage (%s)", e.getClass().getSimpleName());
            }
        });
    }
}
