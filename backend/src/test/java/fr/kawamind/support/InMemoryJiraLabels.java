package fr.kawamind.support;

import fr.kawamind.domain.jira.JiraLabel;
import fr.kawamind.domain.port.JiraLabelRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Label de chaque projet en mémoire (024). */
public class InMemoryJiraLabels implements JiraLabelRepository {

    public final Map<UUID, JiraLabel> labels = new ConcurrentHashMap<>();

    @Override
    public Optional<JiraLabel> find(UUID projectId) {
        return Optional.ofNullable(labels.get(projectId));
    }

    @Override
    public void save(UUID projectId, JiraLabel label, Instant at) {
        labels.put(projectId, label);
    }

    @Override
    public void delete(UUID projectId) {
        labels.remove(projectId);
    }
}
