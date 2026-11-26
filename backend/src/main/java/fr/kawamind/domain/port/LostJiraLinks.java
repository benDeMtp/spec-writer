package fr.kawamind.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Ticket lié à une spécification puis supprimé dans Jira (024, research R8). */
public interface LostJiraLinks {

    void record(UUID sessionId, String issueKey, Instant at);

    Optional<String> find(UUID sessionId);

    void clear(UUID sessionId);
}
