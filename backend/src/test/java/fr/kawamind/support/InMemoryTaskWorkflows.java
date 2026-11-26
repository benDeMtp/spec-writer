package fr.kawamind.support;

import fr.kawamind.domain.port.TaskWorkflowRepository;
import fr.kawamind.domain.task.KnownStatus;
import fr.kawamind.domain.task.TaskWorkflow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Réglages du suivi des tâches en mémoire (023) ; les statuts connus se posent à la main. */
public class InMemoryTaskWorkflows implements TaskWorkflowRepository {

    private final Map<UUID, TaskWorkflow> workflows = new HashMap<>();
    public final Map<UUID, List<KnownStatus>> known = new HashMap<>();

    @Override
    public Optional<TaskWorkflow> find(UUID projectId) {
        return Optional.ofNullable(workflows.get(projectId));
    }

    @Override
    public void save(UUID projectId, TaskWorkflow workflow, Instant at) {
        workflows.put(projectId, workflow);
    }

    @Override
    public void delete(UUID projectId) {
        workflows.remove(projectId);
    }

    @Override
    public List<KnownStatus> knownStatuses(UUID projectId) {
        return new ArrayList<>(known.getOrDefault(projectId, List.of()));
    }
}
