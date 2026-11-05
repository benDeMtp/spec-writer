package dev.specwriter.application.knowledge;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tickets proches des sessions, en mémoire (data-model.md, RelatedIssues). */
public class RelatedIssuesStore {

    private final Map<UUID, RelatedIssues> bySession = new ConcurrentHashMap<>();

    public Optional<RelatedIssues> find(UUID sessionId) {
        return Optional.ofNullable(bySession.get(sessionId));
    }

    public void save(UUID sessionId, RelatedIssues issues) {
        bySession.put(sessionId, issues);
    }

    public void discard(UUID sessionId) {
        bySession.remove(sessionId);
    }
}
