package fr.kawamind.application.agent;

import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.session.Implementation;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.application.task.AgentTasks;
import fr.kawamind.domain.task.TaskTracking;
import java.util.List;
import java.util.UUID;

/**
 * Outil MCP {@code get_feature} (FR-025) : la dernière version en Markdown, identique à l'export
 * (SC-007), et la connaissance enregistrée avec elle. Une feature qui n'est plus à implémenter est
 * introuvable, sans autre détail.
 */
public class GetFeature {

    /**
     * {@code tasks}, {@code progress}, {@code tracking} : tâches suivies (015), relues dans Jira ;
     * {@code readProblem} vaut {@code JIRA_UNREACHABLE} si Jira n'a pas pu être relu.
     */
    public record FeatureContent(UUID id, String title, ImplementationStatus status, int version, String markdown,
            FeatureKnowledge.Knowledge knowledge, String jiraIssueKey, List<AgentTasks.TaskInfo> tasks, Progress progress,
            TaskTracking tracking, String readProblem) {
    }

    public record Progress(int done, int total) {
    }

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final FeatureKnowledge knowledge;
    private final AgentJournal journal;
    private final AgentTasks tasks;

    public GetFeature(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            FeatureKnowledge knowledge, AgentJournal journal) {
        this(sessions, versions, tickets, knowledge, journal, null);
    }

    public GetFeature(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            FeatureKnowledge knowledge, AgentJournal journal, AgentTasks tasks) {
        this.tasks = tasks;
        this.sessions = sessions;
        this.versions = versions;
        this.tickets = tickets;
        this.knowledge = knowledge;
        this.journal = journal;
    }

    public FeatureContent run(String agentName, UUID featureId) {
        return run(agentName, null, featureId);
    }

    /** {@code connection} : la connexion de la passerelle, pour {@code heldByYou} (015, R4). */
    public FeatureContent run(String agentName, String connection, UUID featureId) {
        Implementation implementation = sessions.implementation(featureId)
                .filter(i -> i.status().toImplement()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Session session = sessions.find(featureId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationVersion latest = versions.find(featureId, versions.lastNumber(featureId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        journal.record(agentName, AgentOperation.GET_FEATURE, session.projectId(), featureId);
        AgentTasks.FeatureTasks followed = tasks == null ? new AgentTasks.FeatureTasks(List.of(), 0, 0,
                TaskTracking.ACTIVE, false) : tasks.forFeature(featureId, connection);
        // le statut a pu suivre les tâches relues dans Jira
        ImplementationStatus status = sessions.implementation(featureId).map(Implementation::status)
                .orElse(implementation.status());
        return new FeatureContent(featureId, latest.content().title(), status, latest.number(),
                latest.markdown(), knowledge.of(session.projectId(), latest.knowledge()),
                tickets.findBySession(featureId).map(LinkedJiraTicket::issueKey).orElse(null), followed.tasks(),
                new Progress(followed.done(), followed.total()), followed.tracking(),
                followed.readProblem() ? "JIRA_UNREACHABLE" : null);
    }
}
