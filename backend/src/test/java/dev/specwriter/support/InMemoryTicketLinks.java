package dev.specwriter.support;

import dev.specwriter.domain.jira.TicketLink;
import dev.specwriter.domain.port.TicketLinkRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Liens entre tickets en mémoire, par projet puis par ticket source. */
public class InMemoryTicketLinks implements TicketLinkRepository {

    private final Map<UUID, Map<String, List<TicketLink>>> links = new ConcurrentHashMap<>();

    private Map<String, List<TicketLink>> project(UUID projectId) {
        return links.computeIfAbsent(projectId, p -> new ConcurrentHashMap<>());
    }

    @Override
    public void replace(UUID projectId, String sourceKey, List<TicketLink> list) {
        if (list.isEmpty()) {
            project(projectId).remove(sourceKey);
        } else {
            project(projectId).put(sourceKey, List.copyOf(list.stream().distinct().toList()));
        }
    }

    @Override
    public int retain(UUID projectId, Set<String> keep) {
        List<String> gone = project(projectId).keySet().stream().filter(k -> !keep.contains(k)).toList();
        gone.forEach(project(projectId)::remove);
        return gone.size();
    }

    @Override
    public List<TicketLink> of(UUID projectId, String sourceKey) {
        return project(projectId).getOrDefault(sourceKey, List.of());
    }

    @Override
    public List<Inbound> pointingTo(UUID projectId, String targetKey) {
        List<Inbound> inbound = new ArrayList<>();
        project(projectId).forEach((source, list) -> list.stream().filter(l -> l.targetKey().equals(targetKey))
                .forEach(l -> inbound.add(new Inbound(source, l))));
        inbound.sort(java.util.Comparator.comparing(Inbound::sourceKey));
        return inbound;
    }
}
