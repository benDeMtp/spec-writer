package fr.kawamind.application.task;

import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.LostJiraLinks;
import fr.kawamind.domain.session.SessionListing;
import fr.kawamind.domain.task.SpecTask;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Suites d'un ticket supprimé dans Jira (024, US5, research R8), après son retrait de l'index par la
 * synchronisation : la tâche dont c'est la sous-tâche sort du suivi, et la spécification liée perd
 * son lien, dont la trace est gardée pour le dire sur sa page.
 */
public class ForgetDeletedTickets {

    private static final Logger LOG = Logger.getLogger(ForgetDeletedTickets.class);

    /** Tâches retirées du suivi et liens retirés. */
    public record Result(int tasks, int links) {
    }

    private final TaskDesk desk;
    private final TaskSync sync;
    private final LostJiraLinks lost;

    public ForgetDeletedTickets(TaskDesk desk, TaskSync sync, LostJiraLinks lost) {
        this.desk = desk;
        this.sync = sync;
        this.lost = lost;
    }

    public Result run(UUID projectId, Set<String> removed) {
        if (removed.isEmpty()) {
            return new Result(0, 0);
        }
        int tasks = 0;
        int links = 0;
        for (SessionListing s : desk.sessions.list()) {
            if (!projectId.equals(s.projectId())) {
                continue;
            }
            tasks += desk.locks.with(s.id(), () -> forgetTasks(s.id(), removed));
            Optional<LinkedJiraTicket> ticket = desk.tickets.findBySession(s.id())
                    .filter(t -> removed.contains(t.issueKey()));
            if (ticket.isPresent()) {
                desk.tickets.delete(s.id());
                lost.record(s.id(), ticket.get().issueKey(), desk.clock.now());
                links++;
            }
        }
        if (tasks + links > 0) {
            LOG.infof("Jira : %d tâche(s) et %d lien(s) de spécification retirés, tickets supprimés dans Jira", tasks,
                    links);
        }
        return new Result(tasks, links);
    }

    /** Retire les tâches de ces sous-tâches, et leur place parmi les préalables des autres. */
    private int forgetTasks(UUID sessionId, Set<String> removed) {
        List<SpecTask> all = desk.tasks.tasks(sessionId);
        Set<UUID> gone = all.stream().filter(t -> t.isCreated() && removed.contains(t.jiraKey())).map(SpecTask::id)
                .collect(Collectors.toSet());
        if (gone.isEmpty()) {
            return 0;
        }
        List<SpecTask> kept = all.stream().filter(t -> !gone.contains(t.id()))
                .map(t -> t.dependingOn(t.dependsOn().stream().filter(d -> !gone.contains(d)).toList())).toList();
        desk.tasks.replaceAll(sessionId, TaskDesk.renumber(kept));
        sync.applyStatus(sessionId);
        return gone.size();
    }
}
