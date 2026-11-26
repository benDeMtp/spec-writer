package fr.kawamind.infrastructure.persistence.jira;

import fr.kawamind.domain.port.TicketLabels;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Labels des tickets dans {@code ticket_label} (migration V36, research R2). */
@ApplicationScoped
public class SqliteTicketLabels implements TicketLabels {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void replace(UUID projectId, String key, Collection<String> labels) {
        remove(projectId, List.of(key));
        labels.stream().distinct().filter(l -> l != null && !l.isEmpty()).forEach(l -> add(projectId, key, l));
    }

    @Override
    @Transactional
    public void add(UUID projectId, String key, String label) {
        em.createNativeQuery("insert or ignore into ticket_label (project_id, ticket_key, label) values (?1, ?2, ?3)")
                .setParameter(1, projectId.toString()).setParameter(2, key).setParameter(3, label).executeUpdate();
    }

    @Override
    @Transactional
    public boolean has(UUID projectId, String key, String label) {
        return ((Number) em.createNativeQuery(
                "select count(*) from ticket_label where project_id = ?1 and ticket_key = ?2 and label = ?3")
                .setParameter(1, projectId.toString()).setParameter(2, key).setParameter(3, label).getSingleResult())
                .intValue() > 0;
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void retain(UUID projectId, Set<String> keep) {
        List<String> keys = em.createNativeQuery("select distinct ticket_key from ticket_label where project_id = ?1")
                .setParameter(1, projectId.toString()).getResultList();
        remove(projectId, keys.stream().filter(k -> !keep.contains(k)).toList());
    }

    @Override
    @Transactional
    public void remove(UUID projectId, Collection<String> keys) {
        for (String key : keys) {
            em.createNativeQuery("delete from ticket_label where project_id = ?1 and ticket_key = ?2")
                    .setParameter(1, projectId.toString()).setParameter(2, key).executeUpdate();
        }
    }
}
