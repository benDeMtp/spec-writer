package fr.kawamind.application.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueStatus;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.Transition;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.task.StatusCategory;
import java.util.Optional;

/**
 * Transitions et commentaires des sous-tâches (015, research R3, R5). La transition est choisie
 * d'après la catégorie de statut visée : la première, dans l'ordre de Jira, qui y mène ; jamais une
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

    public Result moveTo(JiraEndpoint endpoint, String key, StatusCategory goal) {
        try {
            IssueStatus current = tracker.issueStatus(endpoint, key);
            if (goal.key().equals(current.statusCategory())) {
                return Result.ALREADY;
            }
            Optional<Transition> transition = tracker.transitions(endpoint, key).stream()
                    .filter(t -> goal.key().equals(t.toCategory())).findFirst();
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
