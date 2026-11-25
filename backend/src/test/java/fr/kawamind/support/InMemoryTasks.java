package fr.kawamind.support;

import fr.kawamind.domain.port.TaskRepository;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskTracking;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tâches en mémoire, avec les mêmes règles d'unicité que la base (position, clé Jira). */
public class InMemoryTasks implements TaskRepository {

    private final Map<UUID, List<SpecTask>> bySession = new LinkedHashMap<>();
    private final Map<UUID, TaskProposal> proposals = new LinkedHashMap<>();
    private final Map<UUID, TaskTracking> tracking = new LinkedHashMap<>();
    private final Map<UUID, String> descriptions = new LinkedHashMap<>();

    @Override
    public List<SpecTask> tasks(UUID sessionId) {
        return bySession.getOrDefault(sessionId, List.of()).stream().sorted(Comparator.comparingInt(SpecTask::position))
                .toList();
    }

    @Override
    public void replaceAll(UUID sessionId, List<SpecTask> tasks) {
        if (tasks.stream().map(SpecTask::position).distinct().count() != tasks.size()) {
            throw new IllegalStateException("positions en double");
        }
        bySession.put(sessionId, new ArrayList<>(tasks));
        checkKeys();
    }

    @Override
    public void update(SpecTask task) {
        List<SpecTask> list = bySession.get(task.sessionId());
        int i = indexOf(list, task.id());
        list.set(i, task);
        checkKeys();
    }

    @Override
    public Map<UUID, String> descriptions(UUID sessionId) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (SpecTask t : tasks(sessionId)) {
            if (descriptions.containsKey(t.id())) {
                out.put(t.id(), descriptions.get(t.id()));
            }
        }
        return out;
    }

    @Override
    public void describe(UUID taskId, String description) {
        if (description == null) {
            descriptions.remove(taskId);
        } else {
            descriptions.put(taskId, description);
        }
    }

    private static int indexOf(List<SpecTask> list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalStateException("tâche inconnue");
    }

    private void checkKeys() {
        List<String> keys = bySession.values().stream().flatMap(List::stream).map(SpecTask::jiraKey)
                .filter(java.util.Objects::nonNull).toList();
        if (keys.stream().distinct().count() != keys.size()) {
            throw new IllegalStateException("clé Jira en double");
        }
    }

    @Override
    public Optional<TaskProposal> proposal(UUID sessionId) {
        return Optional.ofNullable(proposals.get(sessionId));
    }

    @Override
    public void saveProposal(UUID sessionId, TaskProposal proposal, Instant at) {
        proposals.put(sessionId, proposal);
    }

    @Override
    public void deleteProposal(UUID sessionId) {
        proposals.remove(sessionId);
    }

    @Override
    public TaskTracking tracking(UUID sessionId) {
        return tracking.getOrDefault(sessionId, TaskTracking.ACTIVE);
    }

    @Override
    public void setTracking(UUID sessionId, TaskTracking value) {
        tracking.put(sessionId, value);
    }
}
