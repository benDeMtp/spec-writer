package fr.kawamind.application.task;

import fr.kawamind.application.jira.JiraAccess;
import fr.kawamind.application.jira.JiraLabelling;
import fr.kawamind.application.jira.JiraScope;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
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
import fr.kawamind.domain.session.Session;
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
    /** Label de reconnaissance (024) ; sans lui (null), les sous-tâches ne sont pas étiquetées. */
    final JiraLabelling labelling;
    /** Périmètre des agents de code (024, US3) ; {@link JiraScope#OPEN} sans label. */
    final JiraScope scope;

    public TaskDesk(SessionRepository sessions, TaskRepository tasks, LinkedTicketRepository tickets, JiraAccess jira,
            IssueTracker tracker, WikiMarkup markup, ClockPort clock, IdGenerator ids, TaskLocks locks) {
        this(sessions, tasks, tickets, jira, tracker, markup, clock, ids, locks, null);
    }

    public TaskDesk(SessionRepository sessions, TaskRepository tasks, LinkedTicketRepository tickets, JiraAccess jira,
            IssueTracker tracker, WikiMarkup markup, ClockPort clock, IdGenerator ids, TaskLocks locks,
            TaskWorkflowRepository workflows) {
        this(sessions, tasks, tickets, jira, tracker, markup, clock, ids, locks, workflows, null);
    }

    public TaskDesk(SessionRepository sessions, TaskRepository tasks, LinkedTicketRepository tickets, JiraAccess jira,
            IssueTracker tracker, WikiMarkup markup, ClockPort clock, IdGenerator ids, TaskLocks locks,
            TaskWorkflowRepository workflows, JiraLabelling labelling) {
        this(sessions, tasks, tickets, jira, tracker, markup, clock, ids, locks, workflows, labelling, JiraScope.OPEN);
    }

    public TaskDesk(SessionRepository sessions, TaskRepository tasks, LinkedTicketRepository tickets, JiraAccess jira,
            IssueTracker tracker, WikiMarkup markup, ClockPort clock, IdGenerator ids, TaskLocks locks,
            TaskWorkflowRepository workflows, JiraLabelling labelling, JiraScope scope) {
        this.scope = scope == null ? JiraScope.OPEN : scope;
        this.labelling = labelling;
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
        // 024 (US1, R3) : le label du projet sur chaque sous-tâche, créée ou reprise
        UUID projectId = labelling == null ? null : sessions.find(sessionId).map(Session::projectId).orElse(null);
        List<String> labels = projectId == null ? List.of() : labelling.forCreation(projectId, List.of());
        List<String> warnings = new ArrayList<>();
        for (SpecTask task : pending) {
            Subtask existing = parent.subtasks().stream()
                    .filter(s -> !used.contains(s.key()) && task.title().equals(s.summary())).findFirst().orElse(null);
            SpecTask created;
            if (existing != null) {
                created = task.created(existing.key()).read(StatusCategory.fromKey(existing.statusCategory())
                        .map(StatusCategory::status).orElse(TaskStatus.TODO), existing.status(), clock.now());
                if (!labels.isEmpty()) {
                    addWarning(warnings, labelling.tag(projectId, endpoint, existing.key(), null));
                }
            } else {
                try {
                    String key = createSubtask(endpoint, ticket.issueKey(), typeId, task, labels, projectId, warnings);
                    created = task.created(key).read(TaskStatus.TODO, null, clock.now());
                } catch (IssueTrackerException e) {
                    DomainException d = e.toDomain();
                    return joined(warnings, "La création de « " + task.title() + " » a échoué : " + d.userMessage()
                            + " Les tâches suivantes restent à créer.");
                }
            }
            used.add(created.jiraKey());
            tasks.update(created);
        }
        return joined(warnings, null);
    }

    /**
     * Une sous-tâche, avec le label du projet. Si Jira refuse le champ des labels (absent de l'écran de
     * création), elle est créée sans, puis le label est ajouté (024, R3).
     */
    private String createSubtask(JiraEndpoint endpoint, String parentKey, String typeId, SpecTask task,
            List<String> labels, UUID projectId, List<String> warnings) {
        String project = project(parentKey);
        String wiki = markup.toWiki(task.outcome());
        try {
            String key = tracker.createSubtask(endpoint, project, parentKey, typeId, task.title(), wiki, labels).key();
            if (!labels.isEmpty()) {
                labelling.created(projectId, key);
            }
            return key;
        } catch (IssueTrackerException e) {
            if (labels.isEmpty() || !JiraLabelling.refusedLabels(e)) {
                throw e;
            }
            String key = tracker.createSubtask(endpoint, project, parentKey, typeId, task.title(), wiki, List.of()).key();
            addWarning(warnings, labelling.tag(projectId, endpoint, key, null));
            return key;
        }
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (warning != null) {
            warnings.add(warning);
        }
    }

    /** Les avertissements des labels, puis le message d'arrêt ; nul s'il n'y a rien à dire. */
    private static String joined(List<String> warnings, String problem) {
        List<String> all = new ArrayList<>(warnings);
        if (problem != null) {
            all.add(problem);
        }
        return all.isEmpty() ? null : String.join(" ", all);
    }
}
