package fr.kawamind.application.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueStatus;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.Transition;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.task.StatusCategory;
import fr.kawamind.domain.task.StatusName;
import fr.kawamind.domain.task.Target;
import fr.kawamind.domain.task.TaskWorkflow;
import java.util.List;
import java.util.Optional;

/**
 * Transitions et commentaires des sous-tâches (015, research R3, R5). La transition est choisie
 * d'après la catégorie de statut visée, jamais une annulation (voir {@link #choose}) ; jamais une
 * chaîne de transitions. Jira injoignable n'est pas un refus : l'appelant met la tâche « à
 * synchroniser ». Un refus de Jira est une erreur, et rien ne change.
 */
public class TaskJira {

    /** Issue d'un envoi à Jira. */
    public enum Result { SENT, ALREADY, UNREACHABLE }

    private final IssueTracker tracker;

    public TaskJira(IssueTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Vers une cible (023, research R4) : une catégorie, comme dans la 015, ou un statut nommé du
     * workflow, atteint par une transition directe qui n'est pas une annulation. Une sous-tâche déjà
     * dans un statut fini ne reçoit rien ({@link Result#ALREADY}).
     */
    public Result moveTo(JiraEndpoint endpoint, String key, Target target) {
        return switch (target) {
            case Target.Category c -> moveTo(endpoint, key, c.category());
            case Target.Status s -> moveTo(endpoint, key, s);
        };
    }

    private Result moveTo(JiraEndpoint endpoint, String key, Target.Status target) {
        try {
            IssueStatus current = tracker.issueStatus(endpoint, key);
            if (target.workflow().finished(current.status(), current.statusCategory())) {
                return Result.ALREADY;
            }
            Optional<Transition> transition = tracker.transitions(endpoint, key).stream()
                    .filter(t -> StatusName.same(t.toStatus(), target.name())).filter(t -> !t.cancellation())
                    .findFirst();
            if (transition.isEmpty()) {
                throw new DomainException(ErrorCode.NO_TRANSITION, "Le workflow Jira de " + key
                        + " n'offre aucune transition directe de « " + current.status() + " » vers « " + target.name()
                        + " ». Faites-la dans Jira.");
            }
            tracker.transition(endpoint, key, transition.get().id());
            return Result.SENT;
        } catch (IssueTrackerException e) {
            return unreachableOrRefused(e, "la transition de " + key);
        }
    }

    /** Démarrage (023) : l'issue, et le statut atteint quand une transition a été envoyée. */
    public record Started(Result result, String status) {
    }

    /**
     * Démarre un ticket encore « à faire » (023, US4) : une transition directe vers la catégorie « en
     * cours », jamais une annulation ni un saut vers un statut fini du projet (« Réalisée »). Un ticket
     * déjà en cours ou plus loin n'est pas touché ({@link Result#ALREADY}).
     */
    public Started start(JiraEndpoint endpoint, String key, TaskWorkflow workflow) {
        try {
            IssueStatus current = tracker.issueStatus(endpoint, key);
            if (!StatusCategory.NEW.key().equals(current.statusCategory())) {
                return new Started(Result.ALREADY, current.status());
            }
            Optional<Transition> transition = tracker.transitions(endpoint, key).stream()
                    .filter(t -> StatusCategory.IN_PROGRESS.key().equals(t.toCategory()))
                    .filter(t -> !t.cancellation()).filter(t -> !workflow.finished(t.toStatus(), t.toCategory()))
                    .findFirst();
            if (transition.isEmpty()) {
                throw new DomainException(ErrorCode.NO_TRANSITION, "Le workflow Jira de " + key
                        + " n'offre aucune transition directe de « " + current.status() + " » vers « En cours ». "
                        + "Faites-la dans Jira.");
            }
            tracker.transition(endpoint, key, transition.get().id());
            return new Started(Result.SENT, transition.get().toStatus());
        } catch (IssueTrackerException e) {
            return new Started(unreachableOrRefused(e, "le démarrage de " + key), null);
        }
    }

    public Result moveTo(JiraEndpoint endpoint, String key, StatusCategory goal) {
        try {
            IssueStatus current = tracker.issueStatus(endpoint, key);
            if (goal.key().equals(current.statusCategory())) {
                return Result.ALREADY;
            }
            List<Transition> toGoal = tracker.transitions(endpoint, key).stream()
                    .filter(t -> goal.key().equals(t.toCategory())).toList();
            Optional<Transition> transition = choose(toGoal, goal);
            if (transition.isEmpty() && !toGoal.isEmpty()) {
                throw new DomainException(ErrorCode.NO_TRANSITION, "Le workflow Jira de " + key
                        + " ne mène à « " + label(goal) + " » que par une annulation ("
                        + toGoal.stream().map(Transition::name).collect(java.util.stream.Collectors.joining(", "))
                        + ") : spec-writer ne l'utilise pas. Faites la transition dans Jira.");
            }
            if (transition.isEmpty()) {
                throw new DomainException(ErrorCode.NO_TRANSITION, "Le workflow Jira de " + key
                        + " n'offre aucune transition directe de « " + current.status() + " » vers « " + label(goal)
                        + " ». Faites-la dans Jira.");
            }
            tracker.transition(endpoint, key, transition.get().id());
            return Result.SENT;
        } catch (IssueTrackerException e) {
            return unreachableOrRefused(e, "la transition de " + key);
        }
    }

    /**
     * Transition retenue parmi celles qui mènent à la catégorie visée : jamais une annulation (elle
     * mène aussi à « terminé ») ; pour terminer, d'abord celle qui dit « terminé », « résolu »,
     * « fermé »… ; sinon la première, dans l'ordre de Jira.
     */
    static Optional<Transition> choose(List<Transition> toGoal, StatusCategory goal) {
        List<Transition> kept = toGoal.stream().filter(t -> !t.cancellation()).toList();
        if (goal == StatusCategory.DONE) {
            Optional<Transition> completion = kept.stream().filter(Transition::completion).findFirst();
            if (completion.isPresent()) {
                return completion;
            }
        }
        return kept.stream().findFirst();
    }

    public Result comment(JiraEndpoint endpoint, String key, String wiki) {
        try {
            tracker.addComment(endpoint, key, wiki);
            return Result.SENT;
        } catch (IssueTrackerException e) {
            return unreachableOrRefused(e, "le commentaire sur " + key);
        }
    }

    /** Nom affiché d'une catégorie de statut. */
    public static String label(StatusCategory category) {
        return switch (category) {
            case NEW -> "À faire";
            case IN_PROGRESS -> "En cours";
            case DONE -> "Terminé";
        };
    }

    static boolean unreachable(IssueTrackerException e) {
        return e.kind() == IssueTrackerException.Kind.UNREACHABLE || e.kind() == IssueTrackerException.Kind.RATE_LIMITED;
    }

    private static Result unreachableOrRefused(IssueTrackerException e, String what) {
        if (unreachable(e)) {
            return Result.UNREACHABLE;
        }
        if (e.kind() == IssueTrackerException.Kind.UNAUTHORIZED) {
            throw e.toDomain();
        }
        String details = e.details().isEmpty() ? "" : " " + String.join(" ; ", e.details()) + ".";
        throw new DomainException(ErrorCode.JIRA_REFUSED, "Jira a refusé " + what + " : rien n'a changé." + details, e);
    }
}
