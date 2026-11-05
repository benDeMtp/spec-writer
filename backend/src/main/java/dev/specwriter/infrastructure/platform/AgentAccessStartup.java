package dev.specwriter.infrastructure.platform;

import dev.specwriter.application.agent.ManageAgentAccess;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/** Au démarrage : fichier de la passerelle réécrit si l'accès est activé, journal purgé (R2, R11). */
@ApplicationScoped
public class AgentAccessStartup {

    @Inject
    ManageAgentAccess access;

    void onStart(@Observes StartupEvent event) {
        access.startup();
    }
}
