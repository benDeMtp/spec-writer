package dev.specwriter.infrastructure.jira;

import dev.specwriter.domain.jira.IssueTrackerException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Au plus 8 requêtes à Jira sur une même seconde glissante, toutes opérations confondues (FR-022a,
 * research.md R13) : une requête en excès attend son tour, dans l'ordre d'arrivée ; au-delà de
 * 10 secondes d'attente, l'opération échoue avec l'erreur de débit, et une interruption la rend
 * injoignable.
 */
final class JiraThrottle {

    static final int PER_SECOND = 8;
    static final Duration MAX_WAIT = Duration.ofSeconds(10);
    /** Une seconde, plus une marge : FR-022a se juge à l'arrivée chez Jira, après la gigue du réseau. */
    static final long SECOND = Duration.ofMillis(1_050).toNanos();

    /** Attente, remplaçable par les tests. */
    interface Sleeper {
        void sleep(long nanos) throws InterruptedException;
    }

    private final int perSecond;
    private final long maxWait;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private final Deque<Long> sent = new ArrayDeque<>();
    /** Équitable : les requêtes en attente passent dans leur ordre d'arrivée. */
    private final ReentrantLock lock = new ReentrantLock(true);

    JiraThrottle() {
        this(PER_SECOND, MAX_WAIT, System::nanoTime, nanos -> Thread.sleep(Duration.ofNanos(nanos)));
    }

    JiraThrottle(int perSecond, Duration maxWait, LongSupplier clock, Sleeper sleeper) {
        this.perSecond = perSecond;
        this.maxWait = maxWait.toNanos();
        this.clock = clock;
        this.sleeper = sleeper;
    }

    void acquire() {
        long deadline = clock.getAsLong() + maxWait;
        try {
            if (!lock.tryLock(maxWait, TimeUnit.NANOSECONDS)) {
                throw rateLimited();
            }
        } catch (InterruptedException e) {
            throw interrupted(e);
        }
        try {
            waitForSlot(deadline);
        } finally {
            lock.unlock();
        }
    }

    private void waitForSlot(long deadline) {
        while (true) {
            long now = clock.getAsLong();
            while (!sent.isEmpty() && now - sent.peekFirst() >= SECOND) {
                sent.pollFirst();
            }
            if (sent.size() < perSecond) {
                sent.addLast(now);
                return;
            }
            long wait = sent.peekFirst() + SECOND - now;
            if (now + wait > deadline) {
                throw rateLimited();
            }
            try {
                sleeper.sleep(Math.max(wait, 1_000_000));
            } catch (InterruptedException e) {
                throw interrupted(e);
            }
        }
    }

    private static IssueTrackerException rateLimited() {
        return new IssueTrackerException(IssueTrackerException.Kind.RATE_LIMITED,
                "Trop de requêtes vers Jira : attente de plus de 10 s", null);
    }

    private static IssueTrackerException interrupted(InterruptedException e) {
        Thread.currentThread().interrupt();
        return new IssueTrackerException(IssueTrackerException.Kind.UNREACHABLE, "Attente de Jira interrompue", e);
    }
}
