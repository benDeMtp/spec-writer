package fr.kawamind.application.task;

import fr.kawamind.application.jira.JiraAccess;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.CreatedIssue;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.jira.ParentIssue;
import fr.kawamind.domain.jira.Subtask;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.TaskRepository;
import fr.kawamind.domain.port.TaskWorkflowRepository;
import fr.kawamind.domain.port.WikiMarkup;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.StatusCategory;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.domain.task.TaskWorkflow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que partagent les cas d'usage des tâches (015) : dépôts, ticket lié, Jira, verrou, horloge. La
 * création des sous-tâches est ici : à la validation, à la relance et à l'ajout manuel (R10).
 */
public class TaskDesk {

    final SessionRepository sessions;
    final TaskRepository tasks;
    final LinkedTicketRepository tickets;
    final JiraAccess jira;
    final IssueTracker tracker;
    final WikiMarkup markup;
    final ClockPort clock;
    final IdGenerator ids;
    final TaskLocks locks;
    /** Suivi des tâches de chaque projet dans Jira (023) ; sans lui (null), comportement de la 015. */
    final TaskWorkflowRepository workflows;

    public TaskDesk(SessionRepository sessions, TaskRepository tasks, LinkedTicketRepository tickets, JiraAccess jira,
            IssueTracker tracker, WikiMarkup markup, ClockPort clock, IdGenerator ids, TaskLocks locks) {
        this(sessions, tasks, tickets, jira, tracker, markup, clock, ids, locks, null);
    }

    public TaskDesk(SessionRepository sessions, TaskRepository tasks, LinkedTicketRepository tickets, JiraAccess jira,
            IssueTracker tracker, WikiMarkup markup, ClockPort clock, IdGenerator ids, TaskLocks locks,
            TaskWorkflowRepository workflows) {
        this.workflows = workflows;
        this.sessions = sessions;
        this.tasks = tasks;
        this.tickets = tickets;
        this.jira = jira;
        this.tracker = tracker;
        this.markup = markup;
        this.clock = clock;
        this.ids = ids;
        this.locks = locks;
    }

    void requireSession(UUID sessionId) {
        sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    /** Le ticket lié ; sans lui, aucune tâche (FR-001a). */
    LinkedJiraTicket ticket(UUID sessionId) {
        return tickets.findBySession(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NO_LINKED_TICKET));
    }

    /** Suivi des tâches du projet de la session (023) ; {@link TaskWorkflow#NONE} sans réglage. */
    TaskWorkflow workflow(UUID sessionId) {
        if (workflows == null) {
            return TaskWorkflow.NONE;
        }
        return sessions.find(sessionId).flatMap(s -> workflows.find(s.projectId())).orElse(TaskWorkflow.NONE);
    }

    JiraEndpoint endpoint(UUID sessionId) {
        return jira.require(sessionId).endpoint();
    }

    /** Projet Jira d'une clé : la sous-tâche est créée dans le projet de sa story. */
    static String project(String key) {
        return key.substring(0, key.lastIndexOf('-'));
    }

    ParentIssue parent(JiraEndpoint endpoint, String key) {
        try {
            ParentIssue parent = tracker.parentIssue(endpoint, key);
            if (parent.subtask()) {
                throw new DomainException(ErrorCode.PARENT_IS_SUBTASK);
            }
            return parent;
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
    }

    static List<SpecTask> renumber(List<SpecTask> list) {
        List<SpecTask> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            out.add(list.get(i).at(i));
        }
        return out;
    }

    /**
     * Crée dans Jira, dans l'ordre, les tâches pas encore créées. Une sous-tâche de même résumé exact,
     * pas encore suivie, est reprise au lieu d'être créée (arrêt entre la création et
     * l'enregistrement). À la première erreur, les suivantes restent à créer : le message est rendu.
     */
    String createRemaining(UUID sessionId) {
        List<SpecTask> pending = tasks.tasks(sessionId).stream().filter(t -> !t.isCreated()).toList();
        if (pending.isEmpty()) {
            return null;
        }
        LinkedJiraTicket ticket = ticket(sessionId);
        JiraEndpoint endpoint = endpoint(sessionId);
        ParentIssue parent = parent(endpoint, ticket.issueKey());
        String typeId;
        try {
            typeId = tracker.subtaskType(endpoint, project(ticket.issueKey()))
                    .orElseThrow(() -> new DomainException(ErrorCode.NO_SUBTASK_TYPE)).id();
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
        Set<String> used = new HashSet<>();
        tasks.tasks(sessionId).stream().filter(SpecTask::isCreated).forEach(t -> used.add(t.jiraKey()));
        for (SpecTask task : pending) {
            Subtask existing = parent.subtasks().stream()
                    .filter(s -> !used.contains(s.key()) && task.title().equals(s.summary())).findFirst().orElse(null);
            SpecTask created;
            if (existing != null) {
                created = task.created(existing.key()).read(StatusCategory.fromKey(existing.statusCategory())
                        .map(StatusCategory::status).orElse(TaskStatus.TODO), existing.status(), clock.now());
            } else {
                try {
                    CreatedIssue issue = tracker.createSubtask(endpoint, project(ticket.issueKey()), ticket.issueKey(),
                            typeId, task.title(), markup.toWiki(task.outcome()));
                    created = task.created(issue.key()).read(TaskStatus.TODO, null, clock.now());
                } catch (IssueTrackerException e) {
                    DomainException d = e.toDomain();
                    return "La création de « " + task.title() + " » a échoué : " + d.userMessage()
                            + " Les tâches suivantes restent à créer.";
                }
            }
            used.add(created.jiraKey());
            tasks.update(created);
        }
        return null;
    }
}
