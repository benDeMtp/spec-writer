package dev.specwriter.application.agent;

import dev.specwriter.domain.agent.AgentAccess;
import dev.specwriter.domain.agent.AgentOperation;
import dev.specwriter.domain.port.AgentAccessRepository;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.session.ImplementationStatus;
import java.util.UUID;

/** Consigne un accès d'agent, sans contenu (FR-028). */
public class AgentJournal {

    private final AgentAccessRepository repository;
    private final ClockPort clock;
    private final IdGenerator ids;

    public AgentJournal(AgentAccessRepository repository, ClockPort clock, IdGenerator ids) {
        this.repository = repository;
        this.clock = clock;
        this.ids = ids;
    }

    void record(String agentName, AgentOperation operation, UUID projectId, UUID sessionId) {
        record(agentName, operation, projectId, sessionId, null, null);
    }

    void record(String agentName, AgentOperation operation, UUID projectId, UUID sessionId, ImplementationStatus oldStatus,
            ImplementationStatus newStatus) {
        repository.record(new AgentAccess(ids.next(), clock.now(), agentName, operation, projectId, sessionId, oldStatus,
                newStatus));
    }
}
