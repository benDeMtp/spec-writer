package fr.kawamind.application.task;

import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.jira.ParentIssue;
import fr.kawamind.domain.jira.Subtask;
import fr.kawamind.domain.port.PerimeterRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.port.TaskLlm;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.sources.PerimeterSnapshot;
import fr.kawamind.domain.sources.Qualification;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.task.ProposedTasks;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.StatusCategory;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskProposalRequest;
import fr.kawamind.domain.task.TaskStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * « Proposer des tâches » (015, US1, FR-001 à FR-003, FR-006, R8, R10) : les sous-tâches déjà
 * présentes sous la story sont reprises comme tâches, puis le modèle propose de quoi compléter, en
 * s'appuyant sur les modules touchés (013) s'il y en a, sinon sur les cas d'usage. Rien n'est envoyé à
 * Jira : la proposition attend d'être relue.
 */
public class ProposeTasks {

    private final TaskDesk desk;
    private final SpecificationRepository versions;
    private final PerimeterRepository perimeters;
    private final TaskLlm llm;
    private final ConsentPolicy consent;

    public ProposeTasks(TaskDesk desk, SpecificationRepository versions, PerimeterRepository perimeters, TaskLlm llm,
            ConsentPolicy consent) {
        this.desk = desk;
        this.versions = versions;
        this.perimeters = perimeters;
        this.llm = llm;
        this.consent = consent;
    }

    public TaskProposal run(UUID sessionId) {
        Session session = desk.sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        LinkedJiraTicket ticket = desk.ticket(sessionId);
        consent.requireGranted(ConsentService.COPILOT);
        JiraEndpoint endpoint = desk.endpoint(sessionId);
        ParentIssue parent = desk.parent(endpoint, ticket.issueKey());
        List<SpecTask> existing = desk.locks.with(sessionId, () -> importSubtasks(sessionId, parent));
        SpecificationVersion version = versions.list(sessionId).stream()
                .max(Comparator.comparingInt(SpecificationVersion::number))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette session n'a pas encore de spécification."));
        SpecificationContent content = version.content();
        List<TaskProposalRequest.ModuleRef> modules = perimeters.snapshot(sessionId).map(PerimeterSnapshot::modules)
                .orElse(List.of()).stream().filter(m -> m.qualification() != Qualification.NOT_IMPACTED)
                .map(m -> new TaskProposalRequest.ModuleRef(m.name(), m.qualification(), m.justification())).toList();
        TaskProposalRequest request = new TaskProposalRequest(session.language(), content.title(), content.summary(),
                content.useCases().stream().map(u -> new TaskProposalRequest.UseCaseRef(u.code(), u.title(), u.goal()))
                        .toList(), modules, existing.stream().map(SpecTask::title).toList(),
                TaskProposal.Bounds.forExisting(existing.size()));
        ProposedTasks proposed;
        try {
            proposed = llm.proposeTasks(request);
        } catch (DomainException e) {
            if (e.code() == ErrorCode.LLM_INVALID_OUTPUT) {
                throw new DomainException(ErrorCode.TASKS_UNAVAILABLE, ErrorCode.TASKS_UNAVAILABLE.defaultMessage(), e);
            }
            throw e;
        }
        TaskProposal proposal = new TaskProposal(request.basing(), proposed.tasks());
        // la réponse est revérifiée ici : le garde-fou l'a déjà fait, mais le domaine ne dépend pas de lui
        List<String> problems = proposal.modelProblems(request.bounds(),
                modules.stream().map(TaskProposalRequest.ModuleRef::name).collect(Collectors.toSet()),
                content.useCases().stream().map(u -> u.code()).collect(Collectors.toSet()),
                Set.copyOf(request.existingTitles()));
        if (!problems.isEmpty()) {
            throw new DomainException(ErrorCode.TASKS_UNAVAILABLE);
        }
        desk.tasks.saveProposal(sessionId, proposal, desk.clock.now());
        return proposal;
    }

    /** Sous-tâches de la story pas encore suivies : reprises, avant les tâches proposées (R10). */
    private List<SpecTask> importSubtasks(UUID sessionId, ParentIssue parent) {
        List<SpecTask> tasks = new ArrayList<>(desk.tasks.tasks(sessionId));
        Set<String> known = tasks.stream().filter(SpecTask::isCreated).map(SpecTask::jiraKey).collect(Collectors.toSet());
        boolean added = false;
        for (Subtask s : parent.subtasks()) {
            if (!known.contains(s.key())) {
                TaskStatus status = StatusCategory.fromKey(s.statusCategory()).map(StatusCategory::status)
                        .orElse(TaskStatus.TODO);
                tasks.add(SpecTask.fromJira(desk.ids.next(), sessionId, tasks.size(), s.summary(), s.key(), status,
                        s.status(), desk.clock.now()));
                added = true;
            }
        }
        if (added) {
            desk.tasks.replaceAll(sessionId, tasks);
        }
        return tasks;
    }
}
