package dev.specwriter.infrastructure.jira;

import dev.specwriter.domain.jira.IssueTrackerException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Au plus 8 requêtes à Jira sur une même seconde, toutes opérations confondues (FR-022a) : une
 * requête en excès attend son tour ; au-delà de 10 secondes d'attente, l'opération échoue avec
 * l'erreur de débit.
 */
final class JiraThrottle {

    static final int PER_SECOND = 8;
    static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final long SECOND = Duration.ofSeconds(1).toNanos();

    private final int perSecond;
    private final long maxWait;
    private final Deque<Long> sent = new ArrayDeque<>();

    JiraThrottle() {
        this(PER_SECOND, MAX_WAIT);
    }

    JiraThrottle(int perSecond, Duration maxWait) {
        this.perSecond = perSecond;
        this.maxWait = maxWait.toNanos();
    }

    synchronized void acquire() {
        long deadline = System.nanoTime() + maxWait;
        while (true) {
            long now = System.nanoTime();
            while (!sent.isEmpty() && now - sent.peekFirst() >= SECOND) {
                sent.pollFirst();
            }
            if (sent.size() < perSecond) {
                sent.addLast(now);
                return;
            }
            long wait = sent.peekFirst() + SECOND - now;
            if (now + wait > deadline) {
                throw new IssueTrackerException(IssueTrackerException.Kind.RATE_LIMITED,
                        "Trop de requêtes vers Jira : attente de plus de 10 s", null);
            }
            try {
                Thread.sleep(Duration.ofNanos(Math.max(wait, 1_000_000)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IssueTrackerException(IssueTrackerException.Kind.RATE_LIMITED, "Attente de Jira interrompue",
                        e);
            }
        }
    }
}
