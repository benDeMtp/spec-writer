package dev.specwriter.application.agent;

import dev.specwriter.domain.agent.AgentOperation;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.session.Implementation;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Session;
import java.util.UUID;

/**
 * Outil MCP {@code set_feature_status} (FR-027) : READY → IN_PROGRESS et IN_PROGRESS → IMPLEMENTED
 * seulement. Chaque changement est consigné avec l'ancien et le nouveau statut ; l'utilisateur peut
 * rétablir le précédent.
 */
public class AgentChangeStatus {

    public record Changed(UUID id, ImplementationStatus status) {
    }

    private final SessionRepository sessions;
    private final ClockPort clock;
    private final AgentJournal journal;

    public AgentChangeStatus(SessionRepository sessions, ClockPort clock, AgentJournal journal) {
        this.sessions = sessions;
        this.clock = clock;
        this.journal = journal;
    }

    public Changed run(String agentName, UUID featureId, ImplementationStatus target) {
        if (target == null) {
            throw new DomainException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        Session session = sessions.find(featureId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Implementation current = sessions.implementation(featureId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Implementation next = current.change(target, ImplementationStatus.Actor.AGENT, false, clock.now());
        if (!next.equals(current)) {
            sessions.saveImplementation(featureId, next);
            journal.record(agentName, AgentOperation.SET_STATUS, session.projectId(), featureId, current.status(),
                    next.status());
        }
        return new Changed(featureId, next.status());
    }
}
