package dev.specwriter.infrastructure.persistence.agent;

import dev.specwriter.domain.agent.AgentAccess;
import dev.specwriter.domain.agent.AgentOperation;
import dev.specwriter.domain.port.AgentAccessRepository;
import dev.specwriter.domain.session.ImplementationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Réglage et journal des accès des agents, en SQL natif (V13). */
@ApplicationScoped
public class AgentAccessJpaRepository implements AgentAccessRepository {

    @Inject
    EntityManager em;

    @Override
    public boolean enabled() {
        Number value = (Number) em.createNativeQuery("select enabled from agent_access_settings where id = 1").getSingleResult();
        return value.intValue() == 1;
    }

    @Override
    @Transactional
    public void setEnabled(boolean enabled) {
        em.createNativeQuery("update agent_access_settings set enabled = ?1 where id = 1").setParameter(1, enabled ? 1 : 0)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void record(AgentAccess a) {
        em.createNativeQuery("insert into agent_access_log (id, at, agent_name, operation, project_id, session_id,"
                + " old_status, new_status) values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
                .setParameter(1, a.id().toString())
                .setParameter(2, a.at().toString())
                .setParameter(3, a.agentName())
                .setParameter(4, a.operation().name())
                .setParameter(5, text(a.projectId()))
                .setParameter(6, text(a.sessionId()))
                .setParameter(7, a.oldStatus() == null ? null : a.oldStatus().name())
                .setParameter(8, a.newStatus() == null ? null : a.newStatus().name())
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Line> page(int startAt, int max) {
        // la feature est nommée par la session ; projet et session supprimés laissent un nom vide
        List<Object[]> rows = em.createNativeQuery("select l.at, l.agent_name, l.operation, p.name, s.name, l.old_status,"
                + " l.new_status from agent_access_log l left join project p on p.id = l.project_id"
                + " left join session s on s.id = l.session_id order by l.at desc, l.id limit ?1 offset ?2")
                .setParameter(1, max).setParameter(2, startAt).getResultList();
        return rows.stream().map(r -> new Line(Instant.parse((String) r[0]), (String) r[1],
                AgentOperation.valueOf((String) r[2]), (String) r[3], (String) r[4], status(r[5]), status(r[6]))).toList();
    }

    @Override
    public int count() {
        return ((Number) em.createNativeQuery("select count(*) from agent_access_log").getSingleResult()).intValue();
    }

    @Override
    @Transactional
    public int purgeBefore(Instant limit) {
        return em.createNativeQuery("delete from agent_access_log where at < ?1").setParameter(1, limit.toString())
                .executeUpdate();
    }

    private static String text(UUID id) {
        return id == null ? null : id.toString();
    }

    private static ImplementationStatus status(Object value) {
        return value == null ? null : ImplementationStatus.valueOf((String) value);
    }
}
