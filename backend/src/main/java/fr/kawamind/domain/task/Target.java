package fr.kawamind.domain.task;

import java.util.Objects;

/**
 * Cible d'une transition de sous-tâche (023, research R4) : une catégorie de statut (015), ou un
 * statut nommé du workflow, avec ce qui compte comme fini pour reconnaître une tâche déjà arrivée.
 */
public sealed interface Target {

    record Category(StatusCategory category) implements Target {
        public Category {
            Objects.requireNonNull(category);
        }
    }

    record Status(String name, TaskWorkflow workflow) implements Target {
        public Status {
            Objects.requireNonNull(name);
            Objects.requireNonNull(workflow);
        }
    }
}
