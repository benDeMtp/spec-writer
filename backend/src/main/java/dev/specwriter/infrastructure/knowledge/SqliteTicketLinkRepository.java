package dev.specwriter.infrastructure.knowledge;

import dev.specwriter.domain.jira.TicketLink;
import dev.specwriter.domain.jira.TicketLink.Direction;
import dev.specwriter.domain.jira.TicketLink.Kind;
import dev.specwriter.domain.port.TicketLinkRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Liens entre tickets dans la table {@code ticket_link} (migration V14, research.md R29). */
@ApplicationScoped
public class SqliteTicketLinkRepository implements TicketLinkRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void replace(UUID projectId, String sourceKey, List<TicketLink> links) {
        String project = projectId.toString();
        em.createNativeQuery("delete from ticket_link where project_id = ?1 and source_key = ?2")
                .setParameter(1, project).setParameter(2, sourceKey).executeUpdate();
        for (TicketLink link : links.stream().distinct().toList()) {
            em.createNativeQuery("""
                    insert or ignore into ticket_link (project_id, source_key, kind, target_key, link_type, direction, label)
                    values (?1, ?2, ?3, ?4, ?5, ?6, ?7)""")
                    .setParameter(1, project).setParameter(2, sourceKey).setParameter(3, link.kind().name())
                    .setParameter(4, link.targetKey()).setParameter(5, link.linkType())
                    .setParameter(6, link.direction() == null ? null : link.direction().name())
                    .setParameter(7, link.label()).executeUpdate();
        }
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public int retain(UUID projectId, Set<String> keep) {
        String project = projectId.toString();
        List<String> sources = em.createNativeQuery(
                "select distinct source_key from ticket_link where project_id = ?1").setParameter(1, project)
                .getResultList();
        int removed = 0;
        for (String source : sources) {
            if (!keep.contains(source)) {
                em.createNativeQuery("delete from ticket_link where project_id = ?1 and source_key = ?2")
                        .setParameter(1, project).setParameter(2, source).executeUpdate();
                removed++;
            }
        }
        return removed;
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<TicketLink> of(UUID projectId, String sourceKey) {
        List<Object[]> rows = em.createNativeQuery("""
                select kind, target_key, link_type, direction, label from ticket_link
                where project_id = ?1 and source_key = ?2 order by kind, target_key, link_type""")
                .setParameter(1, projectId.toString()).setParameter(2, sourceKey).getResultList();
        return rows.stream().map(r -> link(r[0], r[1], r[2], r[3], r[4])).toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Inbound> pointingTo(UUID projectId, String targetKey) {
        List<Object[]> rows = em.createNativeQuery("""
                select source_key, kind, target_key, link_type, direction, label from ticket_link
                where project_id = ?1 and target_key = ?2 order by source_key, kind, link_type""")
                .setParameter(1, projectId.toString()).setParameter(2, targetKey).getResultList();
        return rows.stream().map(r -> new Inbound((String) r[0], link(r[1], r[2], r[3], r[4], r[5]))).toList();
    }

    private static TicketLink link(Object kind, Object target, Object type, Object direction, Object label) {
        return new TicketLink(Kind.valueOf((String) kind), (String) target, (String) type,
                direction == null ? null : Direction.valueOf((String) direction), (String) label);
    }
}
