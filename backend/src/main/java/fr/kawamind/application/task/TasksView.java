package fr.kawamind.application.task;

import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskTracking;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Les tâches d'une spécification telles que l'écran les montre (015, contracts/rest-api.md).
 *
 * @param proposeBlocked  raison pour laquelle la proposition est indisponible, ou {@code null}
 * @param closedHolders   tâches tenues par une connexion d'agent fermée
 * @param readProblem     Jira n'a pas pu être relu : les statuts sont les derniers connus
 * @param creationProblem échec de la dernière création, rendu par la validation ou sa relance
 */
public record TasksView(String linkedIssueKey, String issueUrlPrefix, ErrorCode proposeBlocked, TaskTracking tracking,
        int done, int total, Instant lastReadAt, boolean readProblem, int pendingSync, String creationProblem,
        boolean hasProposal, List<SpecTask> tasks, Set<UUID> closedHolders) {

    public TasksView {
        tasks = List.copyOf(tasks);
        closedHolders = Set.copyOf(closedHolders);
    }

    public TasksView withCreationProblem(String problem) {
        return new TasksView(linkedIssueKey, issueUrlPrefix, proposeBlocked, tracking, done, total, lastReadAt,
                readProblem, pendingSync, problem, hasProposal, tasks, closedHolders);
    }
}
