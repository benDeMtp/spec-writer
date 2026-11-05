package dev.specwriter.application.specification;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opérations de l'IA en cours, par session (feature 002, research.md R8). Une suppression est
 * refusée tant qu'une opération est ouverte sur la session (FR-019).
 */
public class OperationRegistry {

    /** Opération ouverte ; la fermer la retire du registre. */
    public interface Operation extends AutoCloseable {
        @Override
        void close();
    }

    private final Map<UUID, Integer> running = new ConcurrentHashMap<>();

    public Operation begin(UUID sessionId) {
        running.merge(sessionId, 1, Integer::sum);
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                running.computeIfPresent(sessionId, (id, n) -> n > 1 ? n - 1 : null);
            }
        };
    }

    public boolean running(UUID sessionId) {
        return running.containsKey(sessionId);
    }
}
