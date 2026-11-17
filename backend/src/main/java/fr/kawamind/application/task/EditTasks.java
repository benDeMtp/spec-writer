package fr.kawamind.application.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskOrigin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tâches validées modifiées par l'utilisateur (015, contracts/rest-api.md) : ajout manuel aussitôt
 * créé dans Jira, renommage repris dans le résumé Jira, ordre et préalables, retrait d'une tâche
 * pas encore créée. Une sous-tâche créée n'est jamais supprimée (FR-008).
 */
public class EditTasks {

    private final TaskDesk desk;
    private final ReadTasks read;

    public EditTasks(TaskDesk desk, ReadTasks read) {
        this.desk = desk;
        this.read = read;
    }

    public TasksView add(UUID sessionId, String title, String outcome, List<UUID> dependsOn) {
        desk.requireSession(sessionId);
        desk.ticket(sessionId);
        String problem = desk.locks.with(sessionId, () -> {
            List<SpecTask> tasks = new ArrayList<>(desk.tasks.tasks(sessionId));
            requireKnown(tasks, dependsOn);
            tasks.add(SpecTask.proposed(desk.ids.next(), sessionId, tasks.size(), title, outcome, Basis.NONE,
                    TaskOrigin.MANUAL, dependsOn));
            desk.tasks.replaceAll(sessionId, tasks);
            return desk.createRemaining(sessionId);
        });
        return read.view(sessionId, false).withCreationProblem(problem);
    }

    /** Champs absents : inchangés. Les préalables doivent précéder la tâche après le déplacement. */
    public TasksView edit(UUID sessionId, UUID taskId, String title, Integer position, List<UUID> dependsOn) {
        desk.requireSession(sessionId);
        desk.locks.with(sessionId, () -> {
            List<SpecTask> tasks = new ArrayList<>(desk.tasks.tasks(sessionId));
            SpecTask task = find(tasks, taskId);
            int index = tasks.indexOf(task);
            if (title != null && !title.strip().equals(task.title())) {
                SpecTask renamed = task.renamed(title);
                if (task.isCreated()) {
                    try {
                        desk.tracker.updateSummary(desk.endpoint(sessionId), task.jiraKey(), renamed.title());
                    } catch (IssueTrackerException e) {
                        throw e.toDomain();
                    }
                }
                task = renamed;
            }
            if (dependsOn != null) {
                requireKnown(tasks, dependsOn);
                task = task.dependingOn(dependsOn);
            }
            tasks.set(index, task);
            if (position != null) {
                if (position < 0 || position >= tasks.size()) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED, "Position hors de la liste.");
                }
                tasks.remove(index);
                tasks.add(position, task);
            }
            List<SpecTask> ordered = TaskDesk.renumber(tasks);
            requireOrdered(ordered);
            desk.tasks.replaceAll(sessionId, ordered);
            return null;
        });
        return read.view(sessionId, false);
    }

    public TasksView delete(UUID sessionId, UUID taskId) {
        desk.requireSession(sessionId);
        desk.locks.with(sessionId, () -> {
            List<SpecTask> tasks = new ArrayList<>(desk.tasks.tasks(sessionId));
            SpecTask task = find(tasks, taskId);
            if (task.isCreated()) {
                throw new DomainException(ErrorCode.CANNOT_DELETE_CREATED);
            }
            tasks.remove(task);
            List<SpecTask> kept = tasks.stream().map(t -> t.dependingOn(t.dependsOn().stream()
                    .filter(d -> !d.equals(taskId)).toList())).toList();
            desk.tasks.replaceAll(sessionId, TaskDesk.renumber(kept));
            return null;
        });
        return read.view(sessionId, false);
    }

    private static SpecTask find(List<SpecTask> tasks, UUID taskId) {
        return tasks.stream().filter(t -> t.id().equals(taskId)).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private static void requireKnown(List<SpecTask> tasks, List<UUID> dependsOn) {
        for (UUID d : dependsOn) {
            find(tasks, d);
        }
    }

    private static void requireOrdered(List<SpecTask> tasks) {
        Map<UUID, Integer> positions = new HashMap<>();
        tasks.forEach(t -> positions.put(t.id(), t.position()));
        for (SpecTask t : tasks) {
            for (UUID d : t.dependsOn()) {
                if (positions.get(d) >= t.position()) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "Un préalable doit précéder la tâche « " + t.title() + " ».");
                }
            }
        }
    }
}
