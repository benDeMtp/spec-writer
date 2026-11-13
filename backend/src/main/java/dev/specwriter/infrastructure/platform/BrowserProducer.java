package dev.specwriter.infrastructure.platform;

import dev.specwriter.domain.port.BrowserPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Navigateurs du poste, ou simulés en test et en E2E (T057). */
@ApplicationScoped
public class BrowserProducer {

    @ConfigProperty(name = "specwriter.browser.simulated", defaultValue = "false")
    boolean simulated;

    @Produces
    @ApplicationScoped
    BrowserPort browsers() {
        return simulated ? new SimulatedBrowsers() : DesktopBrowsers.system();
    }
}
