package fr.kawamind.infrastructure.platform;

import fr.kawamind.application.lifecycle.GracefulStop;
import fr.kawamind.infrastructure.web.TabOpener;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import org.jboss.logging.Logger;

/**
 * Canal de l'instance (019, research.md R2) : ouvert seulement par le processus qui tient le verrou
 * du dossier de données, donc ni en dev mode ni en test. Une demande d'ouverture ouvre un onglet
 * avec un nouveau jeton ; pendant l'arrêt, la réponse est « STOPPING ».
 */
@ApplicationScoped
public class InstanceChannel {

    private static final Logger LOG = Logger.getLogger(InstanceChannel.class);

    @Inject
    TabOpener opener;

    @Inject
    GracefulStop stop;

    private ChannelServer server;

    void onStart(@Observes StartupEvent event) {
        InstanceLock.heldDataDir().ifPresent(this::open);
    }

    void open(Path dataDir) {
        Path socket = dataDir.resolve(ChannelProtocol.SOCKET);
        try {
            server = ChannelServer.open(socket, this::handle);
        } catch (IOException | RuntimeException e) {
            // chemin trop long pour une socket, par exemple : l'outil marche, sans réouverture
            LOG.warnf("Canal de l'instance indisponible (%s) : un second lancement ne pourra pas rouvrir l'outil",
                    e.getClass().getSimpleName());
        }
    }

    String handle(String line) {
        if (stop.stopping()) {
            return ChannelProtocol.reply(ChannelProtocol.Status.STOPPING, null);
        }
        if (!ChannelProtocol.isOpenRequest(line)) {
            return ChannelProtocol.reply(ChannelProtocol.Status.UNSUPPORTED, null);
        }
        TabOpener.Result result = opener.open();
        return result.status() == TabOpener.Status.OPENED ? ChannelProtocol.reply(ChannelProtocol.Status.OPENED, null)
                : ChannelProtocol.reply(ChannelProtocol.Status.PRINT, result.url());
    }

    void onStop(@Observes ShutdownEvent event) {
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                LOG.debugf("Canal de l'instance : fermeture incomplète (%s)", e.getClass().getSimpleName());
            }
        }
    }
}
