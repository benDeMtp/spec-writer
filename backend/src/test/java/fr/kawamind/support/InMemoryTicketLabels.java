package fr.kawamind.support;

import fr.kawamind.domain.port.TicketLabels;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Labels des tickets en mémoire, par projet puis par ticket (024). */
public class InMemoryTicketLabels implements TicketLabels {

    public final Map<UUID, Map<String, Set<String>>> labels = new ConcurrentHashMap<>();

    private Map<String, Set<String>> project(UUID projectId) {
        return labels.computeIfAbsent(projectId, p -> new ConcurrentHashMap<>());
    }

    @Override
    public void replace(UUID projectId, String key, Collection<String> list) {
        project(projectId).put(key, new LinkedHashSet<>(list));
    }

    @Override
    public void add(UUID projectId, String key, String label) {
        project(projectId).computeIfAbsent(key, k -> new LinkedHashSet<>()).add(label);
    }

    @Override
    public boolean has(UUID projectId, String key, String label) {
        return project(projectId).getOrDefault(key, Set.of()).contains(label);
    }

    @Override
    public void retain(UUID projectId, Set<String> keep) {
        project(projectId).keySet().removeIf(k -> !keep.contains(k));
    }

    @Override
    public void remove(UUID projectId, Collection<String> keys) {
        keys.forEach(project(projectId)::remove);
    }
}
