package fr.kawamind.application.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.task.TaskWorkflow;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * La story de la feature suit ses tâches dans Jira (023, US4, research R7) : en cours au démarrage
 * si elle était encore à faire ; au statut d'arrivée du projet quand toutes ses tâches sont finies.
 * Un échec ne bloque jamais la tâche : il est rendu à l'agent et écrit au journal, sans rejeu.
 */
public class StoryFollow {

    private static final Logger LOG = Logger.getLogger(StoryFollow.class);
    static final String UNREACHABLE = "Jira est injoignable : la story %s n'a pas été déplacée vers « %s ». "
            + "Faites-la avancer dans Jira.";

    private final TaskDesk desk;
    private final TaskJira jira;

    public StoryFollow(TaskDesk desk, TaskJira jira) {
        this.desk = desk;
        this.jira = jira;
    }

    /** Première prise d'une tâche : la story « à faire » passe en cours ; nulle si rien n'a bougé. */
    public StoryMove started(UUID featureId) {
        Optional<String> key = storyKey(featureId);
        if (key.isEmpty()) {
            return null;
        }
        String to = "en cours";
        try {
            JiraEndpoint endpoint = desk.endpoint(featureId);
            TaskJira.Started started = jira.start(endpoint, key.get(), desk.workflow(featureId));
            return switch (started.result()) {
                case SENT -> new StoryMove(key.get(), StoryMove.Outcome.MOVED, started.status(), null);
                case ALREADY -> null;
                case UNREACHABLE -> failed(key.get(), to, UNREACHABLE.formatted(key.get(), to));
            };
        } catch (DomainException e) {
            return failed(key.get(), to, e.userMessage());
        }
    }

    /** Toutes les tâches finies, ou feature sans tâches implémentée : la story passe au statut d'arrivée. */
    public StoryMove allDone(UUID featureId) {
        Optional<String> key = storyKey(featureId);
        if (key.isEmpty()) {
            return null;
        }
        TaskWorkflow workflow = desk.workflow(featureId);
        if (!workflow.configured()) {
            // jamais fermée par l'outil (FR-018)
            return new StoryMove(key.get(), StoryMove.Outcome.SKIPPED, null, null);
        }
        String to = workflow.arrivalStatus();
        try {
            TaskJira.Result result = jira.moveTo(desk.endpoint(featureId), key.get(), workflow.completion());
            return switch (result) {
                case SENT -> new StoryMove(key.get(), StoryMove.Outcome.MOVED, to, null);
                case ALREADY -> new StoryMove(key.get(), StoryMove.Outcome.ALREADY, to, null);
                case UNREACHABLE -> failed(key.get(), to, UNREACHABLE.formatted(key.get(), to));
            };
        } catch (DomainException e) {
            return failed(key.get(), to, e.userMessage());
        }
    }

    private Optional<String> storyKey(UUID featureId) {
        return desk.tickets.findBySession(featureId).map(LinkedJiraTicket::issueKey);
    }

    private static StoryMove failed(String key, String to, String message) {
        LOG.warnf("Story %s non déplacée vers « %s » : %s", key, to, message);
        return new StoryMove(key, StoryMove.Outcome.FAILED, to, message);
    }
}
