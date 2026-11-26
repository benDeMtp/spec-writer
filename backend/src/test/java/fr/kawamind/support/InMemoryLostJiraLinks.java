package fr.kawamind.support;

import fr.kawamind.domain.port.LostJiraLinks;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tickets liés supprimés dans Jira, en mémoire (024). */
public class InMemoryLostJiraLinks implements LostJiraLinks {

    public final Map<UUID, String> lost = new ConcurrentHashMap<>();

    @Override
    public void record(UUID sessionId, String issueKey, Instant at) {
        lost.put(sessionId, issueKey);
    }

    @Override
    public Optional<String> find(UUID sessionId) {
        return Optional.ofNullable(lost.get(sessionId));
    }

    @Override
    public void clear(UUID sessionId) {
        lost.remove(sessionId);
    }
}
