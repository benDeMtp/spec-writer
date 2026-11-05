package dev.specwriter.domain.port;

import dev.specwriter.domain.agent.AgentAccess;
import dev.specwriter.domain.agent.AgentOperation;
import dev.specwriter.domain.session.ImplementationStatus;
import java.time.Instant;
import java.util.List;

/** Réglage de l'accès des agents (désactivé par défaut) et journal de leurs accès (FR-024, FR-028). */
public interface AgentAccessRepository {

    boolean enabled();

    void setEnabled(boolean enabled);

    void record(AgentAccess access);

    /** Ligne lue : noms du projet et de la feature au moment de la lecture, absents s'ils ont disparu. */
    record Line(Instant at, String agentName, AgentOperation operation, String projectName, String featureTitle,
            ImplementationStatus oldStatus, ImplementationStatus newStatus) {
    }

    /** Les plus récentes d'abord. */
    List<Line> page(int startAt, int max);

    int count();

    /** Supprime les entrées antérieures à {@code limit} ; rend leur nombre. */
    int purgeBefore(Instant limit);
}
