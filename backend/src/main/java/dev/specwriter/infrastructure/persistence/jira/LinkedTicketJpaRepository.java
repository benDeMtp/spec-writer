package dev.specwriter.infrastructure.persistence.jira;

import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.LinkedTicketRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LinkedTicketJpaRepository implements LinkedTicketRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(LinkedJiraTicket t) {
        LinkedTicketEntity e = new LinkedTicketEntity();
        e.sessionId = t.sessionId().toString();
        e.issueKey = t.issueKey();
        e.issueUrl = t.issueUrl();
        e.issueType = t.issueType();
        e.linkedVersionNumber = t.linkedVersionNumber();
        e.lastKnownUpdated = t.lastKnownUpdated();
        e.lastKnownDescriptionHash = t.lastKnownDescriptionHash();
        e.createdOrImportedAt = t.createdOrImportedAt();
        e.lastPushedAt = t.lastPushedAt();
        e.syncState = t.syncState().name();
        em.merge(e);
    }

    @Override
    @Transactional
    public Optional<LinkedJiraTicket> findBySession(UUID sessionId) {
        return Optional.ofNullable(em.find(LinkedTicketEntity.class, sessionId.toString()))
                .map(LinkedTicketJpaRepository::toDomain);
    }

    @Override
    @Transactional
    public Optional<LinkedJiraTicket> findByIssueKey(String issueKey) {
        return em.createQuery("select t from LinkedTicketEntity t where t.issueKey = :k", LinkedTicketEntity.class)
                .setParameter("k", issueKey).getResultStream().findFirst().map(LinkedTicketJpaRepository::toDomain);
    }

    @Override
    @Transactional
    public List<LinkedJiraTicket> all() {
        return em.createQuery("select t from LinkedTicketEntity t", LinkedTicketEntity.class).getResultList().stream()
                .map(LinkedTicketJpaRepository::toDomain).toList();
    }

    private static LinkedJiraTicket toDomain(LinkedTicketEntity e) {
        return new LinkedJiraTicket(UUID.fromString(e.sessionId), e.issueKey, e.issueUrl, e.issueType,
                e.linkedVersionNumber, e.lastKnownUpdated, e.lastKnownDescriptionHash, e.createdOrImportedAt,
                e.lastPushedAt, LinkedJiraTicket.SyncState.valueOf(e.syncState));
    }
}
