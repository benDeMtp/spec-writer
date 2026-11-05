package dev.specwriter.application.agent;

import dev.specwriter.domain.agent.AgentOperation;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.Implementation;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.UUID;

/**
 * Outil MCP {@code get_feature} (FR-025) : la dernière version en Markdown, identique à l'export
 * (SC-007), et la connaissance enregistrée avec elle. Une feature qui n'est plus à implémenter est
 * introuvable, sans autre détail.
 */
public class GetFeature {

    public record FeatureContent(UUID id, String title, ImplementationStatus status, int version, String markdown,
            FeatureKnowledge.Knowledge knowledge, String jiraIssueKey) {
    }

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final FeatureKnowledge knowledge;
    private final AgentJournal journal;

    public GetFeature(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            FeatureKnowledge knowledge, AgentJournal journal) {
        this.sessions = sessions;
        this.versions = versions;
        this.tickets = tickets;
        this.knowledge = knowledge;
        this.journal = journal;
    }

    public FeatureContent run(String agentName, UUID featureId) {
        Implementation implementation = sessions.implementation(featureId)
                .filter(i -> i.status().toImplement()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Session session = sessions.find(featureId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationVersion latest = versions.find(featureId, versions.lastNumber(featureId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        journal.record(agentName, AgentOperation.GET_FEATURE, session.projectId(), featureId);
        return new FeatureContent(featureId, latest.content().title(), implementation.status(), latest.number(),
                latest.markdown(), knowledge.of(session.projectId(), latest.knowledge()),
                tickets.findBySession(featureId).map(LinkedJiraTicket::issueKey).orElse(null));
    }
}
