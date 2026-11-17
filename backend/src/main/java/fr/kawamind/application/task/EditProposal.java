package fr.kawamind.application.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskProposal;
import java.util.UUID;

/**
 * Corrections de la proposition avant validation (015, FR-004) : l'écran renomme, fusionne,
 * supprime, réordonne et ajoute, puis enregistre la liste entière. Rien n'est envoyé à Jira.
 */
public class EditProposal {

    private final TaskDesk desk;

    public EditProposal(TaskDesk desk) {
        this.desk = desk;
    }

    public TaskProposal save(UUID sessionId, TaskProposal corrected) {
        desk.requireSession(sessionId);
        TaskProposal current = desk.tasks.proposal(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Aucune proposition en cours."));
        for (int i = 0; i < corrected.tasks().size(); i++) {
            ProposedTask t = corrected.tasks().get(i);
            if (t.title().length() > SpecTask.MAX_TITLE || t.outcome().length() > SpecTask.MAX_OUTCOME) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Tâche " + (i + 1) + " : titre de "
                        + SpecTask.MAX_TITLE + " caractères au plus, résultat de " + SpecTask.MAX_OUTCOME + ".");
            }
            for (int d : t.dependsOn()) {
                if (d < 0 || d >= i) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "Tâche " + (i + 1) + " : un préalable doit désigner une tâche qui la précède.");
                }
            }
        }
        TaskProposal saved = new TaskProposal(current.basing(), corrected.tasks());
        desk.tasks.saveProposal(sessionId, saved, desk.clock.now());
        return saved;
    }

    public void discard(UUID sessionId) {
        desk.requireSession(sessionId);
        desk.tasks.deleteProposal(sessionId);
    }

    /** Proposition en cours, avec les tâches déjà suivies. */
    public java.util.Optional<TaskProposal> current(UUID sessionId) {
        desk.requireSession(sessionId);
        return desk.tasks.proposal(sessionId);
    }
}
