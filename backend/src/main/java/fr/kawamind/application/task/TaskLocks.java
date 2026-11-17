package fr.kawamind.application.task;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Un verrou par spécification (015) : deux agents qui prennent la même tâche, ou un agent et
 * l'utilisateur, ne lisent et n'écrivent pas les tâches en même temps (FR-012).
 */
public class TaskLocks {

    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public <T> T with(UUID sessionId, Supplier<T> action) {
        Object lock = locks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (lock) {
            return action.get();
        }
    }
}
