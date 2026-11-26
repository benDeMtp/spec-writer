package fr.kawamind.application.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.TaskWorkflowRepository;
import fr.kawamind.domain.task.KnownStatus;
import fr.kawamind.domain.task.StatusCategory;
import fr.kawamind.domain.task.StatusName;
import fr.kawamind.domain.task.TaskWorkflow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rubrique « Suivi des tâches dans Jira » des réglages du projet (023, US3) : statut d'arrivée d'une
 * tâche implémentée et statuts finis, proposés parmi les statuts connus de l'index (research R2).
 */
public class ManageTaskWorkflow {

    /** Le réglage (nul sans réglage) et les statuts proposés ; un statut réglé absent de l'index y est ajouté. */
    public record View(String arrivalStatus, List<String> finishedStatuses, List<KnownStatus> known) {
    }

    private final ProjectRepository projects;
    private final TaskWorkflowRepository workflows;
    private final ClockPort clock;

    public ManageTaskWorkflow(ProjectRepository projects, TaskWorkflowRepository workflows, ClockPort clock) {
        this.projects = projects;
        this.workflows = workflows;
        this.clock = clock;
    }

    public View view(UUID projectId) {
        requireProject(projectId);
        return view(projectId, workflows.find(projectId).orElse(TaskWorkflow.NONE));
    }

    public View save(UUID projectId, String arrivalStatus, List<String> finishedStatuses) {
        requireProject(projectId);
        TaskWorkflow workflow = TaskWorkflow.of(arrivalStatus, finishedStatuses);
        workflows.knownStatuses(projectId).stream()
                .filter(s -> StatusName.same(s.name(), workflow.arrivalStatus()))
                .filter(s -> StatusCategory.NEW.key().equals(s.category())).findFirst().ifPresent(s -> {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le statut d'arrivée d'une tâche implémentée"
                            + " ne peut pas être un statut « à faire » (" + s.name() + ").");
                });
        workflows.save(projectId, workflow, clock.now());
        return view(projectId, workflow);
    }

    public void remove(UUID projectId) {
        requireProject(projectId);
        workflows.delete(projectId);
    }

    private View view(UUID projectId, TaskWorkflow workflow) {
        List<KnownStatus> known = new ArrayList<>(workflows.knownStatuses(projectId));
        for (String name : workflow.finishedStatuses()) {
            if (known.stream().noneMatch(k -> StatusName.same(k.name(), name))) {
                known.add(new KnownStatus(name, null, false));
            }
        }
        return new View(workflow.arrivalStatus(), workflow.finishedStatuses(), known);
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
