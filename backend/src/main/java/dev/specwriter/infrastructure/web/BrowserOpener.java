package dev.specwriter.infrastructure.web;

import dev.specwriter.infrastructure.platform.BrowserLauncher;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Ouvre l'application dans le navigateur au démarrage, avec le jeton de lancement (R14, R15). */
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

    void onStart(@Observes StartupEvent event) {
        URI url = URI.create("http://127.0.0.1:" + port + "/?launch=" + tokens.launchToken());
        if (!openBrowser) {
            if (printLaunchUrl) {
                System.out.println("Ouvrez spec-writer dans votre navigateur : " + url);
            }
            return;
        }
        if (!BrowserLauncher.system().open(url)) {
            // Le jeton n'est affiché que dans la console locale, jamais dans le fichier de journal.
            System.out.println("Ouvrez spec-writer dans votre navigateur : " + url);
        }
        LOG.infof("spec-writer est disponible sur http://127.0.0.1:%d", port);
    }
}
