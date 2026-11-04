package dev.specwriter.domain.port;

import dev.specwriter.domain.session.SessionRelation;
import java.util.Optional;
import java.util.UUID;

/** Ticket fermé lié à une session (colonnes de la session, V10). */
public interface SessionRelationRepository {

    Optional<SessionRelation> find(UUID sessionId);

    void save(SessionRelation relation);
}
