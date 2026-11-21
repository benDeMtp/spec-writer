package fr.kawamind.application.lifecycle;

import fr.kawamind.domain.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.logging.Logger;

/**
 * Arrêt propre de l'outil (019, research.md R4), le même pour Quitter, ⌘Q, Ctrl+C et SIGTERM :
 * les indexations et les tours sont interrompus comme par leurs commandes d'arrêt, puis attendus
 * 5 s au plus. Le texte déjà reçu d'une réponse est gardé, marqué incomplet, par le tour lui-même.
 */
public class GracefulStop {

    static final Duration WAIT = Duration.ofSeconds(5);
    static final Duration POLL = Duration.ofMillis(100);
    private static final Logger LOG = Logger.getLogger(GracefulStop.class);

    /** Pause entre deux vérifications ; simulée en test. */
    @FunctionalInterface
    public interface Pause {
        void pause(Duration duration) throws InterruptedException;
    }

    private final RunningWork work;
    private final ExitPort exit;
    private final ClockPort clock;
    private final Pause pause;
    private final AtomicBoolean requested = new AtomicBoolean();
    private final AtomicBoolean interrupted = new AtomicBoolean();

    public GracefulStop(RunningWork work, ExitPort exit, ClockPort clock, Pause pause) {
        this.work = work;
        this.exit = exit;
        this.clock = clock;
        this.pause = pause;
    }

    /**
     * Quitter depuis l'interface : interrompt, attend, puis arrête le processus. Sans effet si un
     * arrêt est déjà demandé.
     *
     * @return {@code false} si l'arrêt était déjà demandé
     */
    public boolean stop() {
        if (!requested.compareAndSet(false, true)) {
            return false;
        }
        int count = interruptAndWait();
        LOG.infof("Arrêt demandé depuis l'interface (%d travaux interrompus)", count);
        exit.exit(0);
        return true;
    }

    /** Arrêt venu d'ailleurs (crochet de la JVM : ⌘Q, Ctrl+C, SIGTERM) : interrompt et attend. */
    public void onShutdown() {
        requested.set(true);
        interruptAndWait();
    }

    /** Vrai dès qu'un arrêt est demandé. */
    public boolean stopping() {
        return requested.get();
    }

    private int interruptAndWait() {
        if (!interrupted.compareAndSet(false, true)) {
            return 0;
        }
        int count = work.cancelAll();
        Instant limit = clock.now().plus(WAIT);
        try {
            while (work.interruptibleRunning() && clock.now().isBefore(limit)) {
                pause.pause(POLL);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return count;
    }
}
