package fr.kawamind.infrastructure.persistence.project;

import fr.kawamind.domain.port.ConfluencePageStates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Table {@code confluence_page} (V27) : identifiant, version et date ; supprimée avec sa racine. */
@ApplicationScoped
public class ConfluencePageSqliteStates implements ConfluencePageStates {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, PageState> of(UUID sourceId) {
        List<Object[]> rows = em.createNativeQuery(
                        "select page_id, version, updated_at from confluence_page where source_id = ?1 order by rowid")
                .setParameter(1, sourceId.toString()).getResultList();
        Map<String, PageState> out = new LinkedHashMap<>();
        for (Object[] r : rows) {
            out.put((String) r[0], new PageState(((Number) r[1]).intValue(),
                    r[2] == null ? null : Instant.parse((String) r[2])));
        }
        return out;
    }

    @Override
    @Transactional
    public void replace(UUID sourceId, Map<String, PageState> pages) {
        em.createNativeQuery("delete from confluence_page where source_id = ?1").setParameter(1, sourceId.toString())
                .executeUpdate();
        for (Map.Entry<String, PageState> e : pages.entrySet()) {
            em.createNativeQuery(
                            "insert into confluence_page (source_id, page_id, version, updated_at) values (?1, ?2, ?3, ?4)")
                    .setParameter(1, sourceId.toString()).setParameter(2, e.getKey())
                    .setParameter(3, e.getValue().version())
                    .setParameter(4, e.getValue().updatedAt() == null ? null : e.getValue().updatedAt().toString())
                    .executeUpdate();
        }
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Set<UUID>> roots(UUID projectId) {
        List<Object[]> rows = em.createNativeQuery("""
                        select p.page_id, p.source_id from confluence_page p
                        join project_doc_source s on s.id = p.source_id where s.project_id = ?1""")
                .setParameter(1, projectId.toString()).getResultList();
        Map<String, Set<UUID>> out = new HashMap<>();
        for (Object[] r : rows) {
            out.computeIfAbsent((String) r[0], k -> new LinkedHashSet<>()).add(UUID.fromString((String) r[1]));
        }
        return out;
    }
}
