package fr.kawamind.infrastructure.knowledge;

import fr.kawamind.domain.jira.IssueSort;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.jira.ListedIssue;
import fr.kawamind.domain.port.ListedIssues;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tickets de l'index joints à leur lien avec une spécification du projet, triés et paginés par
 * SQLite (017, research.md R1). L'ordre est construit à partir du tri validé, jamais d'un texte reçu.
 */
@ApplicationScoped
public class SqliteListedIssues implements ListedIssues {

    private static final String STATUS_RANK = """
            case k.status_category when 'new' then 0 when 'indeterminate' then 1 when 'done' then 2 else 3 end""";
    private static final String SYNC_RANK = """
            case x.sync_state when 'CONFLICT' then 0 when 'LOCAL_AHEAD' then 1 when 'IN_SYNC' then 2 else 3 end""";
    private static final String FROM = """
            from knowledge_item k
            left join (select l.issue_key, l.sync_state, l.session_id, s.project_id
                       from linked_jira_ticket l join session s on s.id = l.session_id) x
                   on x.issue_key = k.ref and x.project_id = k.project_id
            where k.project_id = ?1 and k.kind = 'TICKET' and k.chunk = 0""";

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<ListedIssue> page(UUID projectId, IssueSort sort, int offset, int limit) {
        List<Object[]> rows = em.createNativeQuery("select k.ref, k.title, k.status, k.status_category, "
                + "k.source_updated_at, x.sync_state, x.session_id " + FROM + " order by " + order(sort)
                + " limit ?2 offset ?3").setParameter(1, projectId.toString()).setParameter(2, limit)
                .setParameter(3, offset).getResultList();
        return rows.stream().map(r -> new ListedIssue((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                r[4] == null ? null : Instant.parse((String) r[4]),
                ListedIssue.Sync.of(r[5] == null ? null : LinkedJiraTicket.SyncState.valueOf((String) r[5])),
                r[6] == null ? null : UUID.fromString((String) r[6]))).toList();
    }

    @Override
    @Transactional
    public int count(UUID projectId) {
        return ((Number) em.createNativeQuery("select count(*) " + FROM).setParameter(1, projectId.toString())
                .getSingleResult()).intValue();
    }

    static String order(IssueSort sort) {
        String dir = sort.direction() == IssueSort.Direction.ASC ? "asc" : "desc";
        return switch (sort.field()) {
            case UPDATED -> "k.source_updated_at " + dir + ", k.ref asc";
            case STATUS -> STATUS_RANK + " " + dir + ", k.status asc, k.ref asc";
            case SYNC -> SYNC_RANK + " " + dir + ", k.source_updated_at desc, k.ref asc";
        };
    }
}
