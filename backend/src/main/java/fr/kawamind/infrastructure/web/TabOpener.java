package fr.kawamind.infrastructure.web;

import fr.kawamind.application.settings.ManageBrowser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Ouvre un nouvel onglet sur l'outil en marche, avec un nouveau jeton à usage unique (019,
 * research.md R2, R6) : demande d'un second lancement ou clic sur le Dock de macOS. Sans navigateur
 * (réglage ou échec), l'adresse est rendue à l'appelant, qui l'affiche dans sa console ; elle n'est
 * jamais journalisée.
 */
@ApplicationScoped
public class TabOpener {

    public enum Status { OPENED, PRINT }

    /** {@code url} n'est présente que pour {@link Status#PRINT}. */
    public record Result(Status status, String url) {
    }

    private final LaunchTokenService tokens;
    private final ManageBrowser browser;
    private final int port;
    private final boolean openBrowser;

    @Inject
    public TabOpener(LaunchTokenService tokens, ManageBrowser browser,
            @ConfigProperty(name = "quarkus.http.port") int port,
            @ConfigProperty(name = "specwriter.open-browser", defaultValue = "true") boolean openBrowser) {
        this.tokens = tokens;
        this.browser = browser;
        this.port = port;
        this.openBrowser = openBrowser;
    }

    public Result open() {
        String url = BrowserOpener.launchUrl(port, tokens.issue()).toString();
        if (openBrowser && browser.open(url).opened()) {
            return new Result(Status.OPENED, null);
        }
        return new Result(Status.PRINT, url);
    }
}
