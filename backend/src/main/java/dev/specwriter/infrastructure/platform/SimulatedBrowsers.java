package dev.specwriter.infrastructure.platform;

import dev.specwriter.domain.port.BrowserPort;
import dev.specwriter.domain.settings.Browser;
import java.util.List;

/**
 * Navigateurs simulés pour les tests et les E2E ({@code specwriter.browser.simulated=true}) : deux
 * navigateurs listés, aucune ouverture réelle.
 */
public final class SimulatedBrowsers implements BrowserPort {

    static final List<Browser> INSTALLED = List.of(new Browser("simule-a", "Navigateur simulé A"),
            new Browser("simule-b", "Navigateur simulé B"));

    @Override
    public List<Browser> installed() {
        return INSTALLED;
    }

    @Override
    public boolean open(String browserId, String url) {
        return browserId == null || INSTALLED.stream().anyMatch(b -> b.id().equals(browserId));
    }
}
