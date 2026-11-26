package fr.kawamind.infrastructure.persistence.jira;

import fr.kawamind.domain.port.LostJiraLinks;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ticket lié supprimé dans Jira, dans {@code lost_jira_link} (migration V36, research R8). */
@ApplicationScoped
public class SqliteLostJiraLinks implements LostJiraLinks {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void record(UUID sessionId, String issueKey, Instant at) {
        em.createNativeQuery("""
                insert into lost_jira_link (session_id, issue_key, lost_at) values (?1, ?2, ?3)
                on conflict (session_id) do update set issue_key = excluded.issue_key, lost_at = excluded.lost_at""")
                .setParameter(1, sessionId.toString()).setParameter(2, issueKey).setParameter(3, at.toString())
                .executeUpdate();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<String> find(UUID sessionId) {
        List<String> rows = em.createNativeQuery("select issue_key from lost_jira_link where session_id = ?1")
                .setParameter(1, sessionId.toString()).getResultList();
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public void clear(UUID sessionId) {
        em.createNativeQuery("delete from lost_jira_link where session_id = ?1").setParameter(1, sessionId.toString())
                .executeUpdate();
    }
}
