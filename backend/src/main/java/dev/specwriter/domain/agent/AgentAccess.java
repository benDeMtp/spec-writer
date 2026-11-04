package dev.specwriter.domain.agent;

import dev.specwriter.domain.session.ImplementationStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Une ligne du journal des accès des agents (FR-028, research.md R11) : jamais de contenu, seulement
 * l'opération, la feature ou le projet concernés et, pour un changement de statut, l'ancien et le
 * nouveau. Le nom annoncé n'est pas vérifié : il sert à la lecture du journal.
 */
public record AgentAccess(UUID id, Instant at, String agentName, AgentOperation operation, UUID projectId,
        UUID sessionId, ImplementationStatus oldStatus, ImplementationStatus newStatus) {

    public static final int MAX_NAME = 80;
    public static final String UNKNOWN = "agent sans nom";

    public AgentAccess {
        Objects.requireNonNull(id);
        Objects.requireNonNull(at);
        Objects.requireNonNull(operation);
        agentName = agentName(agentName);
    }

    /** Nom lisible : sans caractère de contrôle, 80 caractères au plus, jamais vide. */
    public static String agentName(String announced) {
        String clean = announced == null ? "" : announced.replaceAll("\\p{Cntrl}", " ").strip();
        if (clean.isEmpty()) {
            return UNKNOWN;
        }
        return clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
    }
}
