package dev.specwriter.domain.session;

import java.util.Objects;
import java.util.UUID;

/**
 * Ticket fermé auquel la story de la session sera liée par « relates to » à sa création dans Jira
 * (FR-035), et l'état de ce lien.
 */
public record SessionRelation(UUID sessionId, String relatesTo, Link link) {

    public enum Link {
        /** Story pas encore créée. */
        PENDING,
        LINKED,
        /** Story créée, lien refusé ou Jira injoignable : à réessayer. */
        FAILED
    }

    public SessionRelation {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(relatesTo);
        link = link == null ? Link.PENDING : link;
    }

    public SessionRelation with(Link newLink) {
        return new SessionRelation(sessionId, relatesTo, newLink);
    }
}
