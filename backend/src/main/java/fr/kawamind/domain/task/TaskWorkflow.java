package fr.kawamind.domain.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Suivi des tâches d'un projet dans Jira (023) : le statut où arrive une tâche implémentée, et les
 * statuts où une tâche compte comme finie (le statut d'arrivée toujours, et toute la catégorie
 * « terminé »). {@link #NONE} : pas de réglage, la catégorie décide comme dans la 015.
 */
public record TaskWorkflow(String arrivalStatus, List<String> finishedStatuses) {

    public static final int MAX_NAME = 100;
    public static final int MAX_FINISHED = 50;
    public static final TaskWorkflow NONE = new TaskWorkflow(null, List.of());

    public TaskWorkflow {
        finishedStatuses = finishedStatuses == null ? List.of() : List.copyOf(finishedStatuses);
    }

    /** Réglage validé : statut d'arrivée de 1 à 100 caractères, ajouté en tête des statuts finis s'il manque. */
    public static TaskWorkflow of(String arrivalStatus, List<String> finishedStatuses) {
        String arrival = arrivalStatus == null ? "" : arrivalStatus.strip();
        if (arrival.isEmpty() || arrival.length() > MAX_NAME) {
            throw invalid("Le statut d'arrivée d'une tâche implémentée fait de 1 à " + MAX_NAME + " caractères.");
        }
        List<String> finished = new ArrayList<>();
        finished.add(arrival);
        Set<String> seen = new HashSet<>();
        seen.add(StatusName.normalize(arrival));
        boolean arrivalListed = false;
        for (String raw : finishedStatuses == null ? List.<String>of() : finishedStatuses) {
            String name = raw == null ? "" : raw.strip();
            if (name.isEmpty() || name.length() > MAX_NAME) {
                throw invalid("Un statut fini fait de 1 à " + MAX_NAME + " caractères.");
            }
            if (StatusName.same(name, arrival) && !arrivalListed) {
                arrivalListed = true;
                continue;
            }
            if (!seen.add(StatusName.normalize(name))) {
                throw invalid("Le statut « " + name + " » est coché deux fois.");
            }
            finished.add(name);
        }
        if (finished.size() > MAX_FINISHED) {
            throw invalid("Cochez " + MAX_FINISHED + " statuts finis au plus.");
        }
        return new TaskWorkflow(arrival, finished);
    }

    public boolean configured() {
        return arrivalStatus != null;
    }

    /** Fini : catégorie « terminé », ou statut d'arrivée, ou statut coché. */
    public boolean finished(String statusName, String category) {
        return StatusCategory.DONE.key().equals(category)
                || finishedStatuses.stream().anyMatch(s -> StatusName.same(s, statusName));
    }

    /** Statut d'une tâche d'après son statut Jira ; vide pour une catégorie inconnue. */
    public Optional<TaskStatus> statusOf(String statusName, String category) {
        if (finished(statusName, category)) {
            return Optional.of(TaskStatus.DONE);
        }
        return StatusCategory.fromKey(category).map(StatusCategory::status);
    }

    /** Cible de la fin d'une tâche : le statut d'arrivée, ou la catégorie « terminé » sans réglage. */
    public Target completion() {
        return configured() ? new Target.Status(arrivalStatus, this) : new Target.Category(StatusCategory.DONE);
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message);
    }
}
