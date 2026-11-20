package fr.kawamind.infrastructure.platform;

import fr.kawamind.application.lifecycle.GracefulStop;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * ⌘Q, Ctrl+C, SIGTERM et la fin de Quitter passent par l'arrêt de Quarkus : les travaux en cours y
 * sont interrompus et attendus, comme pour Quitter (019, research.md R4).
 */
@ApplicationScoped
public class GracefulStopOnShutdown {

    @Inject
    GracefulStop stop;

    void onStop(@Observes ShutdownEvent event) {
        stop.onShutdown();
    }
}
