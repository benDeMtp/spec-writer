package fr.kawamind.domain.port;

import fr.kawamind.domain.jira.LinkedJiraTicket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tickets Jira liés aux sessions ; supprimés avec leur session. */
public interface LinkedTicketRepository {

    void save(LinkedJiraTicket ticket);

    Optional<LinkedJiraTicket> findBySession(UUID sessionId);

    Optional<LinkedJiraTicket> findByIssueKey(String issueKey);

    List<LinkedJiraTicket> all();

    /** Retire le lien d'une session (024 : ticket supprimé dans Jira). */
    void delete(UUID sessionId);
}
