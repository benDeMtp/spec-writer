package dev.specwriter.infrastructure.web;

import dev.specwriter.application.settings.ManageBrowser;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Ouvre l'application au démarrage, avec le jeton de lancement (R14, R15), dans le navigateur
 * choisi dans la configuration ou, à défaut, dans celui du système (013, T057).
 */
@ApplicationScoped
public class BrowserOpener {

    private static final Logger LOG = Logger.getLogger(BrowserOpener.class);

    @ConfigProperty(name = "specwriter.open-browser", defaultValue = "true")
    boolean openBrowser;

    /** Affiche l'URL de lancement dans la console au lieu d'ouvrir le navigateur (poste sans bureau). */
    @ConfigProperty(name = "specwriter.print-launch-url", defaultValue = "false")
    boolean printLaunchUrl;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @Inject
    LaunchTokenService tokens;

    @Inject
    ManageBrowser browser;

    void onStart(@Observes StartupEvent event) {
        URI url = launchUrl(port, tokens.launchToken());
        if (!openBrowser) {
            if (printLaunchUrl) {
                System.out.println("Ouvrez spec-writer dans votre navigateur : " + url);
            }
            return;
        }
        if (!browser.open(url.toString()).opened()) {
            // Le jeton n'est affiché que dans la console locale, jamais dans le fichier de journal.
            System.out.println("Ouvrez spec-writer dans votre navigateur : " + url);
        }
        LOG.infof("spec-writer est disponible sur http://127.0.0.1:%d", port);
    }

    /** Adresse d'ouverture de spec-writer avec un jeton de lancement. */
    static URI launchUrl(int port, String token) {
        return URI.create("http://127.0.0.1:" + port + "/?launch=" + token);
    }
}
