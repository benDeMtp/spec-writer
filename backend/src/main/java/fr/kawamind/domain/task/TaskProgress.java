package fr.kawamind.domain.task;

import fr.kawamind.domain.session.ImplementationStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Avancement des tâches suivies (015, data-model.md) : seules les tâches créées dans Jira comptent,
 * et une tâche introuvable dans Jira sort du calcul. Le choix de la prochaine tâche est déterministe
 * (FR-010, FR-016) : il ne dépend que des tâches, de la connexion appelante et des connexions vivantes.
 */
public final class TaskProgress {

    /** Réponse de « prochaine tâche ». */
    public sealed interface Next {

        /** {@code resume} : la tâche était déjà en cours (même connexion ou connexion fermée). */
        record Task(SpecTask task, boolean resume) implements Next {
        }

        /** Toutes les tâches suivies sont terminées. */
        record NoneLeft() implements Next {
        }

        /** Il en reste, mais tenues par d'autres connexions ouvertes ou bloquées par leurs préalables. */
        record AllHeld(List<String> heldBy) implements Next {

            public AllHeld {
                heldBy = List.copyOf(heldBy);
            }
        }
    }

    private TaskProgress() {
    }

    static boolean tracked(SpecTask t) {
        return t.isCreated() && t.status() != TaskStatus.MISSING;
    }

    public static int done(List<SpecTask> tasks) {
        return (int) tasks.stream().filter(TaskProgress::tracked).filter(t -> t.status() == TaskStatus.DONE).count();
    }

    public static int total(List<SpecTask> tasks) {
        return (int) tasks.stream().filter(TaskProgress::tracked).count();
    }

    /**
     * Statut de la spécification d'après ses tâches (R7) : aucune commencée, prête ; toutes terminées,
     * implémentée ; sinon en cours. Vide sans tâche suivie.
     */
    public static Optional<ImplementationStatus> status(List<SpecTask> tasks) {
        List<SpecTask> followed = tasks.stream().filter(TaskProgress::tracked).toList();
        if (followed.isEmpty()) {
            return Optional.empty();
        }
        if (followed.stream().allMatch(t -> t.status() == TaskStatus.DONE)) {
            return Optional.of(ImplementationStatus.IMPLEMENTED);
        }
        if (followed.stream().allMatch(t -> t.status() == TaskStatus.TODO)) {
            return Optional.of(ImplementationStatus.READY);
        }
        return Optional.of(ImplementationStatus.IN_PROGRESS);
    }

    /**
     * Prochaine tâche pour {@code connection} : sa tâche en cours, sinon la première tâche en cours
     * d'une connexion fermée, sinon la première à faire dont les préalables sont terminés (ou
     * introuvables), sinon plus rien ou tout est tenu.
     */
    public static Next next(List<SpecTask> tasks, String connection, Predicate<String> alive) {
        List<SpecTask> followed = tasks.stream().filter(TaskProgress::tracked).toList();
        for (SpecTask t : followed) {
            if (t.status() == TaskStatus.IN_PROGRESS && t.holder() != null
                    && t.holder().connection().equals(connection)) {
                return new Next.Task(t, true);
            }
        }
        for (SpecTask t : followed) {
            if (t.status() == TaskStatus.IN_PROGRESS && t.holder() != null && !alive.test(t.holder().connection())) {
                return new Next.Task(t, true);
            }
        }
        Set<UUID> finished = tasks.stream().filter(t -> t.status() == TaskStatus.DONE || t.status() == TaskStatus.MISSING)
                .map(SpecTask::id).collect(Collectors.toSet());
        for (SpecTask t : followed) {
            if (t.status() == TaskStatus.TODO && finished.containsAll(t.dependsOn())) {
                return new Next.Task(t, false);
            }
        }
        if (followed.stream().allMatch(t -> t.status() == TaskStatus.DONE)) {
            return new Next.NoneLeft();
        }
        return new Next.AllHeld(followed.stream().filter(t -> t.status() == TaskStatus.IN_PROGRESS && t.holder() != null)
                .map(t -> t.holder().agentName()).distinct().toList());
    }

    /** Une tâche peut être prise : à faire et préalables terminés, ou en cours d'une connexion fermée ou de celle-ci. */
    public static boolean startable(List<SpecTask> tasks, SpecTask task) {
        Set<UUID> finished = tasks.stream().filter(t -> t.status() == TaskStatus.DONE || t.status() == TaskStatus.MISSING)
                .map(SpecTask::id).collect(Collectors.toSet());
        return tracked(task) && task.status() == TaskStatus.TODO && finished.containsAll(task.dependsOn());
    }
}
