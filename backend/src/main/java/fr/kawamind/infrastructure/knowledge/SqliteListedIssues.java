package fr.kawamind.infrastructure.knowledge;

import fr.kawamind.domain.jira.IssueFilters;
import fr.kawamind.domain.jira.IssueSort;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.jira.ListedIssue;
import fr.kawamind.domain.port.ListedIssues;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String LINKED = """
            left join (select l.issue_key, l.sync_state, l.session_id, s.project_id
                       from linked_jira_ticket l join session s on s.id = l.session_id) x
                   on x.issue_key = k.ref and x.project_id = k.project_id""";
    /** Premier niveau (022, research R2) : les tickets sans parent indexé, d'après le lien PARENT. */
    private static final String FROM = """
            from knowledge_item k
            """ + LINKED + """

            left join ticket_link op on op.project_id = k.project_id and op.source_key = k.ref and op.kind = 'PARENT'
            where k.project_id = ?1 and k.kind = 'TICKET' and k.chunk = 0
              and not exists (select 1 from ticket_link p
                              join knowledge_item pk on pk.project_id = p.project_id and pk.ref = p.target_key
                                                    and pk.kind = 'TICKET' and pk.chunk = 0
                              where p.project_id = k.project_id and p.source_key = k.ref and p.kind = 'PARENT')""";
    private static final String COLUMNS = "k.ref, k.title, k.status, k.status_category, k.source_updated_at, "
            + "x.sync_state, x.session_id";

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<ListedIssue> page(UUID projectId, IssueSort sort, int offset, int limit) {
        return page(projectId, sort, IssueFilters.ALL, offset, limit);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<ListedIssue> page(UUID projectId, IssueSort sort, IssueFilters filters, int offset, int limit) {
        var query = em.createNativeQuery("select " + COLUMNS + ", op.target_key " + FROM + filtered(filters, 4)
                + " order by " + order(sort) + " limit ?2 offset ?3").setParameter(1, projectId.toString())
                .setParameter(2, limit).setParameter(3, offset);
        if (filters.label() != null) {
            query.setParameter(4, filters.label());
        }
        List<Object[]> rows = query.getResultList();
        List<ListedIssue> top = rows.stream().map(r -> issue(r, (String) r[7])).toList();
        if (top.isEmpty()) {
            return top;
        }
        Map<String, List<ListedIssue>> children = subtasks(projectId, top.stream().map(ListedIssue::key).toList());
        return top.stream().map(i -> i.withSubtasks(children.getOrDefault(i.key(), List.of()))).toList();
    }

    /** Sous-tâches indexées des tickets de la page, par parent, classées par clé (022, research R2, R3). */
    @SuppressWarnings("unchecked")
    private Map<String, List<ListedIssue>> subtasks(UUID projectId, List<String> parents) {
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < parents.size(); i++) {
            in.append(i == 0 ? "?" : ", ?").append(i + 2);
        }
        var query = em.createNativeQuery("select " + COLUMNS + ", l.target_key from ticket_link l"
                + " join knowledge_item k on k.project_id = l.project_id and k.ref = l.source_key"
                + " and k.kind = 'TICKET' and k.chunk = 0 " + LINKED
                + " where l.project_id = ?1 and l.kind = 'PARENT' and l.target_key in (" + in + ")")
                .setParameter(1, projectId.toString());
        for (int i = 0; i < parents.size(); i++) {
            query.setParameter(i + 2, parents.get(i));
        }
        Map<String, List<ListedIssue>> out = new HashMap<>();
        for (Object[] r : (List<Object[]>) query.getResultList()) {
            out.computeIfAbsent((String) r[7], k -> new ArrayList<>()).add(issue(r, null));
        }
        out.values().forEach(list -> list.sort(Comparator.comparing(ListedIssue::key, ListedIssue.KEY_ORDER)));
        return out;
    }

    private static ListedIssue issue(Object[] r, String parentKey) {
        return new ListedIssue((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                r[4] == null ? null : Instant.parse((String) r[4]),
                ListedIssue.Sync.of(r[5] == null ? null : LinkedJiraTicket.SyncState.valueOf((String) r[5])),
                r[6] == null ? null : UUID.fromString((String) r[6]), parentKey, List.of());
    }

    @Override
    @Transactional
    public int count(UUID projectId) {
        return count(projectId, IssueFilters.ALL);
    }

    @Override
    @Transactional
    public int count(UUID projectId, IssueFilters filters) {
        var query = em.createNativeQuery("select count(*) " + FROM + filtered(filters, 2))
                .setParameter(1, projectId.toString());
        if (filters.label() != null) {
            query.setParameter(2, filters.label());
        }
        return ((Number) query.getSingleResult()).intValue();
    }

    /**
     * Filtres du premier niveau (024, research R6) : le label, en paramètre {@code ?labelIndex}, jamais
     * dans le texte de la requête ; les tickets terminés écartés sauf demande.
     */
    static String filtered(IssueFilters filters, int labelIndex) {
        StringBuilder sql = new StringBuilder();
        if (filters.label() != null) {
            sql.append(" and exists (select 1 from ticket_label tl where tl.project_id = k.project_id")
                    .append(" and tl.ticket_key = k.ref and tl.label = ?").append(labelIndex).append(')');
        }
        if (!filters.done()) {
            sql.append(" and coalesce(k.status_category, '') <> 'done'");
        }
        return sql.toString();
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
