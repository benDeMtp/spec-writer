package fr.kawamind.domain.task;

import fr.kawamind.domain.agent.AgentAccess;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;

/**
 * Connexion d'agent qui tient une tâche (015, research R4) : {@code <pid>-<début du processus en ms>}
 * de la passerelle, nom annoncé par l'agent (nettoyé comme au journal) et heure de prise.
 */
public record Holder(String connection, String agentName, Instant since) {

    public static final int MAX_CONNECTION = 64;

    public Holder {
        if (connection == null || connection.isBlank() || connection.length() > MAX_CONNECTION) {
            throw new DomainException(ErrorCode.BRIDGE_OUTDATED);
        }
        agentName = AgentAccess.agentName(agentName);
        Objects.requireNonNull(since);
    }
}
