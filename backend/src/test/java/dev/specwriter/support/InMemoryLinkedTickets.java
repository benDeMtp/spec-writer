package dev.specwriter.support;

import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.LinkedTicketRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryLinkedTickets implements LinkedTicketRepository {

    public final Map<UUID, LinkedJiraTicket> tickets = new LinkedHashMap<>();

    @Override
    public void save(LinkedJiraTicket ticket) {
        tickets.put(ticket.sessionId(), ticket);
    }

    @Override
    public Optional<LinkedJiraTicket> findBySession(UUID sessionId) {
        return Optional.ofNullable(tickets.get(sessionId));
    }

    @Override
    public Optional<LinkedJiraTicket> findByIssueKey(String issueKey) {
        return tickets.values().stream().filter(t -> t.issueKey().equals(issueKey)).findFirst();
    }

    @Override
    public List<LinkedJiraTicket> all() {
        return new ArrayList<>(tickets.values());
    }
}
