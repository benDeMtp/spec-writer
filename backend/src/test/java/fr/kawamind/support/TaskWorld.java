package fr.kawamind.support;

import fr.kawamind.application.task.TaskDesk;
import fr.kawamind.application.task.TaskLocks;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.port.AgentConnections;
import fr.kawamind.domain.session.Session;
import java.util.HashSet;
import java.util.Set;

/** Décor des tests des tâches (015) : le décor Jira, les tâches en mémoire et des connexions d'agent simulées. */
public class TaskWorld extends JiraWorld {

    public final InMemoryTasks tasks = new InMemoryTasks();
    public final InMemoryPerimeters perimeters = new InMemoryPerimeters();
    public final FakeTaskLlm taskLlm = new FakeTaskLlm();
    public final TaskLocks locks = new TaskLocks();
    /** Connexions fermées ; toutes les autres sont vivantes. */
    public final Set<String> closed = new HashSet<>();
    public final AgentConnections connections = c -> c != null && !closed.contains(c);
    public final InMemoryTaskWorkflows workflows = new InMemoryTaskWorkflows();
    public final TaskDesk desk = new TaskDesk(sessions, tasks, tickets, jira, tracker, markup, clock, ids, locks,
            workflows, new fr.kawamind.application.jira.JiraLabelling(jiraLabels, ticketLabels, tracker),
            new fr.kawamind.application.jira.JiraScope(jiraLabels, ticketLabels, tickets, sessions));

    public TaskWorld() {
        grant(ConsentService.JIRA);
        grant(ConsentService.COPILOT);
        configureJira();
    }

    /** Session générée liée à la story {@code FACT-120} du Jira simulé. */
    public Session linkedSession() {
        Session s = generatedSession();
        link(s, "FACT-120", "d");
        tracker.parents.put("FACT-120", false);
        return s;
    }
}
