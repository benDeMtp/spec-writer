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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Transitions et commentaires des sous-tâches (015, research R3, R5). La transition est choisie
 * d'après la catégorie de statut visée, jamais une annulation (voir {@link #choose}) ; jamais une
 * chaîne de transitions. Jira injoignable n'est pas un refus : l'appelant met la tâche « à
 * synchroniser ». Un refus de Jira est une erreur, et rien ne change.
 *
 * <p>Avant chaque transition, sauf la remise « à faire », le ticket est affecté au propriétaire du
 * jeton (023, US5, research R9). Une affectation refusée n'empêche pas la transition : son motif est
 * rendu ({@link Move#assignProblem()}) et écrit au journal.
 */
public class TaskJira {

    private static final Logger LOG = Logger.getLogger(TaskJira.class);

    /** Issue d'un envoi à Jira. */
    public enum Result { SENT, ALREADY, UNREACHABLE }

    /** Issue d'un envoi, et motif d'une affectation refusée (nul si rien à signaler, 023). */
    public record Move(Result result, String assignProblem) {
    }

    private final IssueTracker tracker;
    /** Login du propriétaire de chaque jeton : relu quand le jeton change (FR-022). */
    private final Map<JiraEndpoint, String> logins = new ConcurrentHashMap<>();

    public TaskJira(IssueTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Vers une cible (023, research R4) : une catégorie, comme dans la 015, ou un statut nommé du
     * workflow, atteint par une transition directe qui n'est pas une annulation. Une sous-tâche déjà
     * dans un statut fini ne reçoit rien ({@link Result#ALREADY}).
     */
    public Result moveTo(JiraEndpoint endpoint, String key, Target target) {
        return move(endpoint, key, target).result();
    }

    /** Comme {@link #moveTo(JiraEndpoint, String, Target)}, avec le motif d'une affectation refusée. */
    public Move move(JiraEndpoint endpoint, String key, Target target) {
        return switch (target) {
            case Target.Category c -> move(endpoint, key, c.category());
            case Target.Status s -> move(endpoint, key, s);
        };
    }

    private Move move(JiraEndpoint endpoint, String key, Target.Status target) {
        try {
            IssueStatus current = tracker.issueStatus(endpoint, key);
            if (target.workflow().finished(current.status(), current.statusCategory())) {
                return new Move(Result.ALREADY, null);
            }
            Optional<Transition> transition = tracker.transitions(endpoint, key).stream()
                    .filter(t -> StatusName.same(t.toStatus(), target.name())).filter(t -> !t.cancellation())
                    .findFirst();
            if (transition.isEmpty()) {
                throw new DomainException(ErrorCode.NO_TRANSITION, "Le workflow Jira de " + key
                        + " n'offre aucune transition directe de « " + current.status() + " » vers « " + target.name()
                        + " ». Faites-la dans Jira.");
            }
            String problem = assign(endpoint, key, current);
            tracker.transition(endpoint, key, transition.get().id());
            return new Move(Result.SENT, problem);
        } catch (IssueTrackerException e) {
            return new Move(unreachableOrRefused(e, "la transition de " + key), null);
        }
    }

    /**
     * Démarrage (023) : l'issue, le statut atteint quand une transition a été envoyée, et le motif
     * d'une affectation refusée.
     */
    public record Started(Result result, String status, String assignProblem) {
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
                return new Started(Result.ALREADY, current.status(), null);
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
            String problem = assign(endpoint, key, current);
            tracker.transition(endpoint, key, transition.get().id());
            return new Started(Result.SENT, transition.get().toStatus(), problem);
        } catch (IssueTrackerException e) {
            return new Started(unreachableOrRefused(e, "le démarrage de " + key), null, null);
        }
    }

    public Result moveTo(JiraEndpoint endpoint, String key, StatusCategory goal) {
        return move(endpoint, key, goal).result();
    }

    private Move move(JiraEndpoint endpoint, String key, StatusCategory goal) {
        try {
            IssueStatus current = tracker.issueStatus(endpoint, key);
            if (goal.key().equals(current.statusCategory())) {
                return new Move(Result.ALREADY, null);
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
            // la remise « à faire » d'une tâche libérée n'est pas un travail du propriétaire du jeton
            String problem = goal == StatusCategory.NEW ? null : assign(endpoint, key, current);
            tracker.transition(endpoint, key, transition.get().id());
            return new Move(Result.SENT, problem);
        } catch (IssueTrackerException e) {
            return new Move(unreachableOrRefused(e, "la transition de " + key), null);
        }
    }

    /**
     * Affecte le ticket au propriétaire du jeton s'il ne l'est pas déjà (023, FR-019 à FR-021). Rend
     * le motif d'un refus, nul sinon ; Jira injoignable est relancé, comme pour la transition.
     */
    private String assign(JiraEndpoint endpoint, String key, IssueStatus current) {
        String login = null;
        try {
            login = logins.computeIfAbsent(endpoint, tracker::currentUser);
            if (login == null || login.equals(current.assignee())) {
                return null;
            }
            tracker.assign(endpoint, key, login);
            return null;
        } catch (IssueTrackerException e) {
            if (unreachable(e)) {
                throw e;
            }
            String why = e.details().isEmpty() ? "Jira a refusé l'affectation." : String.join(" ; ", e.details());
            LOG.warnf("Ticket %s non affecté à %s : %s", key, login, why);
            return key + " n'a pas pu être affecté à " + (login == null ? "votre compte Jira" : login) + " : " + why;
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
