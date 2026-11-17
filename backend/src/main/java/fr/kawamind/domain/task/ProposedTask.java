package fr.kawamind.domain.task;

import java.util.List;
import java.util.Objects;

/**
 * Une tâche de la proposition en cours de relecture (015, R8). {@code dependsOn} : positions,
 * dans la proposition, des tâches préalables ; {@code manual} : ajoutée par l'utilisateur.
 */
public record ProposedTask(String title, String outcome, Basis basis, List<Integer> dependsOn, boolean manual) {

    public ProposedTask {
        title = title == null ? "" : title.strip();
        outcome = outcome == null ? "" : outcome.strip();
        basis = Objects.requireNonNullElse(basis, Basis.NONE);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
