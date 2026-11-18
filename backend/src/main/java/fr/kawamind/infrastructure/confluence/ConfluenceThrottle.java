package fr.kawamind.infrastructure.confluence;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Débit vers Confluence (016, R10) : les requêtes sont espacées pour en envoyer au plus 8 par
 * seconde, avec une marge pour la gigue du réseau ; le serveur de l'entreprise est partagé.
 */
final class ConfluenceThrottle {

    static final int PER_SECOND = 8;
    /** Écart minimal entre deux requêtes : 1,05 s / 8. */
    static final long GAP = Duration.ofMillis(1_050).toNanos() / PER_SECOND;

    /** Attente, remplaçable par les tests. */
    interface Pause {
        void pause(long nanos) throws InterruptedException;
    }

    private final LongSupplier clock;
    private final Pause pause;
    private long next = Long.MIN_VALUE;

    ConfluenceThrottle() {
        this(System::nanoTime, nanos -> Thread.sleep(Duration.ofNanos(nanos)));
    }

    ConfluenceThrottle(LongSupplier clock, Pause pause) {
        this.clock = clock;
        this.pause = pause;
    }

    /** Attend le tour de la requête ; faux si l'attente a été interrompue. */
    synchronized boolean acquire() {
        long now = clock.getAsLong();
        long at = next == Long.MIN_VALUE ? now : Math.max(now, next);
        next = at + GAP;
        if (at > now) {
            try {
                pause.pause(at - now);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
