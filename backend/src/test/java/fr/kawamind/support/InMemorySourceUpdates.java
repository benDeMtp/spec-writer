package fr.kawamind.support;

import fr.kawamind.domain.interview.SourceUpdate;
import fr.kawamind.domain.port.SourceUpdateRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** « À reporter » en mémoire (021, US6). */
public class InMemorySourceUpdates implements SourceUpdateRepository {

    private final Map<UUID, SourceUpdate> updates = new LinkedHashMap<>();

    @Override
    public void save(SourceUpdate update) {
        updates.put(update.id(), update);
    }

    @Override
    public Optional<SourceUpdate> find(UUID id) {
        return Optional.ofNullable(updates.get(id));
    }

    @Override
    public List<SourceUpdate> open(UUID sessionId) {
        return updates.values().stream()
                .filter(u -> u.sessionId().equals(sessionId) && u.state() == SourceUpdate.State.OPEN).toList();
    }

    public List<SourceUpdate> all() {
        return List.copyOf(updates.values());
    }
}
