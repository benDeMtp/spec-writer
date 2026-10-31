package dev.specwriter.infrastructure.platform;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/** Proxy d'entreprise lu une fois au démarrage (T139), pour Jira et le Copilot CLI. */
@ApplicationScoped
public class ProxySettingsProducer {

    private static final Logger LOG = Logger.getLogger(ProxySettingsProducer.class);

    @Produces
    @Singleton
    ProxySettings proxySettings() {
        ProxySettings settings = ProxySettings.fromSystem();
        if (!settings.isEmpty()) {
            LOG.infof("Proxy d'entreprise : %s", settings.describe());
        }
        return settings;
    }
}
