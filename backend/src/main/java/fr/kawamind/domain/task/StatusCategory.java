package fr.kawamind.domain.task;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catégories de statut de Jira, fixes quel que soit le workflow (015, research R3, R6) ; spec-writer
 * choisit ses transitions et lit ses statuts par elles, jamais par le nom du statut.
 */
public enum StatusCategory {
    NEW("new", TaskStatus.TODO),
    IN_PROGRESS("indeterminate", TaskStatus.IN_PROGRESS),
    DONE("done", TaskStatus.DONE);

    private final String key;
    private final TaskStatus status;

    StatusCategory(String key, TaskStatus status) {
        this.key = key;
        this.status = status;
    }

    public String key() {
        return key;
    }

    public TaskStatus status() {
        return status;
    }

    public static Optional<StatusCategory> fromKey(String key) {
        return Arrays.stream(values()).filter(c -> c.key.equals(key)).findFirst();
    }

    /** Catégorie visée pour un statut de tâche ; {@code MISSING} n'en a pas. */
    public static StatusCategory of(TaskStatus status) {
        return switch (status) {
            case TODO -> NEW;
            case IN_PROGRESS -> IN_PROGRESS;
            case DONE -> DONE;
            case MISSING -> throw new IllegalArgumentException("aucune catégorie pour MISSING");
        };
    }
}
