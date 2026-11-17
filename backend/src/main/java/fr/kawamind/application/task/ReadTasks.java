package fr.kawamind.application.task;

import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.AgentConnections;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskProgress;
import fr.kawamind.domain.task.TaskStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Vue des tâches d'une spécification (015, contracts/rest-api.md, {@code GET /tasks}). */
public class ReadTasks {

    private final TaskDesk desk;
    private final AgentConnections connections;

    public ReadTasks(TaskDesk desk, AgentConnections connections) {
        this.desk = desk;
        this.connections = connections;
    }

    public TasksView run(UUID sessionId) {
        desk.requireSession(sessionId);
        return view(sessionId, false);
    }

    TasksView view(UUID sessionId, boolean readProblem) {
        LinkedJiraTicket ticket = desk.tickets.findBySession(sessionId).orElse(null);
        List<SpecTask> tasks = desk.tasks.tasks(sessionId);
        Set<UUID> closed = tasks.stream().filter(t -> t.status() == TaskStatus.IN_PROGRESS && t.holder() != null)
                .filter(t -> !connections.alive(t.holder().connection())).map(SpecTask::id).collect(Collectors.toSet());
        Instant lastRead = tasks.stream().map(SpecTask::lastReadAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        String prefix = ticket == null ? null
                : ticket.issueUrl().substring(0, ticket.issueUrl().length() - ticket.issueKey().length());
        return new TasksView(ticket == null ? null : ticket.issueKey(), prefix,
                ticket == null ? ErrorCode.NO_LINKED_TICKET : null, desk.tasks.tracking(sessionId),
                TaskProgress.done(tasks), TaskProgress.total(tasks), lastRead, readProblem,
                (int) tasks.stream().filter(SpecTask::isPending).count(), null,
                desk.tasks.proposal(sessionId).isPresent(), tasks, closed);
    }
}
