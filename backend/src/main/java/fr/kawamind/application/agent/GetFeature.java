package fr.kawamind.application.agent;

import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.jira.TicketLink;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.port.TicketLinkRepository;
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
            FeatureKnowledge.Knowledge knowledge, String jiraIssueKey, JiraTicket jiraTicket,
            List<AgentTasks.TaskInfo> tasks, Progress progress, TaskTracking tracking, String readProblem) {
    }

    /**
     * Ticket lié à la feature (022, research R5) : son type et son parent, lus dans l'index local ;
     * nuls hors de l'index, et {@code parentKey} nul si ce n'est pas une sous-tâche.
     */
    public record JiraTicket(String key, String issueType, String parentKey) {
    }

    public record Progress(int done, int total) {
    }

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final FeatureKnowledge knowledge;
    private final AgentJournal journal;
    private final AgentTasks tasks;
    /** Index et liens des tickets (022) ; sans eux (null), le ticket lié n'a que sa clé. */
    private final KnowledgeIndex index;
    private final TicketLinkRepository links;

    public GetFeature(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            FeatureKnowledge knowledge, AgentJournal journal) {
        this(sessions, versions, tickets, knowledge, journal, null);
    }

    public GetFeature(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            FeatureKnowledge knowledge, AgentJournal journal, AgentTasks tasks) {
        this(sessions, versions, tickets, knowledge, journal, tasks, null, null);
    }

    public GetFeature(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            FeatureKnowledge knowledge, AgentJournal journal, AgentTasks tasks, KnowledgeIndex index,
            TicketLinkRepository links) {
        this.index = index;
        this.links = links;
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
        String issueKey = tickets.findBySession(featureId).map(LinkedJiraTicket::issueKey).orElse(null);
        return new FeatureContent(featureId, latest.content().title(), status, latest.number(),
                latest.markdown(), knowledge.of(session.projectId(), latest.knowledge()), issueKey,
                issueKey == null ? null : ticket(session.projectId(), issueKey), followed.tasks(),
                new Progress(followed.done(), followed.total()), followed.tracking(),
                followed.readProblem() ? "JIRA_UNREACHABLE" : null);
    }

    /** Type et parent du ticket lié, lus dans l'index ; la clé seule s'il n'y est pas (022, research R5). */
    private JiraTicket ticket(UUID projectId, String key) {
        if (index == null || links == null) {
            return new JiraTicket(key, null, null);
        }
        String type = index.find(projectId, new KnowledgeIndex.Key(KnowledgeKind.TICKET, key, 0))
                .map(KnowledgeItem::issueType).orElse(null);
        String parent = links.of(projectId, key).stream().filter(l -> l.kind() == TicketLink.Kind.PARENT)
                .map(TicketLink::targetKey).findFirst().orElse(null);
        return new JiraTicket(key, type, parent);
    }
}
