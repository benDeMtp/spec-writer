package fr.kawamind.application.agent;

import fr.kawamind.domain.agent.AgentAccess;
import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.port.AgentAccessRepository;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.session.ImplementationStatus;
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

    /** Opération sur une tâche (015, R11) : la clé Jira seulement, jamais le titre ni le compte rendu. */
    public void recordTask(String agentName, AgentOperation operation, UUID projectId, UUID sessionId, String taskKey) {
        repository.record(new AgentAccess(ids.next(), clock.now(), agentName, operation, projectId, sessionId, null,
                null, taskKey));
    }
}
