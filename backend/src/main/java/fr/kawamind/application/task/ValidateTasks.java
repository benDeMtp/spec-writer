package fr.kawamind.application.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskOrigin;
import fr.kawamind.domain.task.TaskProposal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validation de la proposition (015, FR-005 à FR-008, R10) : les tâches s'ajoutent après celles déjà
 * suivies, puis deviennent des sous-tâches de la story, une par une, dans l'ordre. Une création
 * interrompue se relance sans doublon ; rien n'est jamais supprimé dans Jira.
 */
public class ValidateTasks {

    private final TaskDesk desk;
    private final ReadTasks read;

    public ValidateTasks(TaskDesk desk, ReadTasks read) {
        this.desk = desk;
        this.read = read;
    }

    public TasksView run(UUID sessionId) {
        desk.requireSession(sessionId);
        desk.ticket(sessionId);
        String problem = desk.locks.with(sessionId, () -> {
            TaskProposal proposal = desk.tasks.proposal(sessionId)
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Aucune proposition en cours."));
            List<String> problems = proposal.shapeProblems();
            if (proposal.tasks().isEmpty()) {
                problems = List.of("La proposition ne contient aucune tâche.");
            }
            if (!problems.isEmpty()) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, String.join(" ", problems));
            }
            // le type sous-tâche est vérifié avant d'enregistrer quoi que ce soit
            try {
                desk.tracker.subtaskType(desk.endpoint(sessionId), TaskDesk.project(desk.ticket(sessionId).issueKey()))
                        .orElseThrow(() -> new DomainException(ErrorCode.NO_SUBTASK_TYPE));
            } catch (fr.kawamind.domain.jira.IssueTrackerException e) {
                throw e.toDomain();
            }
            List<SpecTask> tasks = new ArrayList<>(desk.tasks.tasks(sessionId));
            int first = tasks.size();
            List<UUID> newIds = new ArrayList<>();
            for (int i = 0; i < proposal.tasks().size(); i++) {
                newIds.add(desk.ids.next());
            }
            for (int i = 0; i < proposal.tasks().size(); i++) {
                ProposedTask p = proposal.tasks().get(i);
                tasks.add(SpecTask.proposed(newIds.get(i), sessionId, first + i, p.title(), p.outcome(), p.basis(),
                        p.manual() ? TaskOrigin.MANUAL : TaskOrigin.PROPOSED,
                        p.dependsOn().stream().map(newIds::get).toList()));
            }
            desk.tasks.replaceAll(sessionId, tasks);
            desk.tasks.deleteProposal(sessionId);
            return desk.createRemaining(sessionId);
        });
        return read.view(sessionId, false).withCreationProblem(problem);
    }

    /** « Créer les tâches restantes » après une création interrompue. */
    public TasksView createRemaining(UUID sessionId) {
        desk.requireSession(sessionId);
        String problem = desk.locks.with(sessionId, () -> desk.createRemaining(sessionId));
        return read.view(sessionId, false).withCreationProblem(problem);
    }
}
