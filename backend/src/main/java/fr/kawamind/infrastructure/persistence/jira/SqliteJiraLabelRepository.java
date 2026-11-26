package fr.kawamind.infrastructure.persistence.jira;

import fr.kawamind.domain.jira.JiraLabel;
import fr.kawamind.domain.port.JiraLabelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Label de chaque projet dans {@code project_jira_label} (migration V36). */
@ApplicationScoped
public class SqliteJiraLabelRepository implements JiraLabelRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<JiraLabel> find(UUID projectId) {
        List<String> rows = em.createNativeQuery("select label from project_jira_label where project_id = ?1")
                .setParameter(1, projectId.toString()).getResultList();
        return rows.stream().findFirst().map(JiraLabel::new);
    }

    @Override
    @Transactional
    public void save(UUID projectId, JiraLabel label, Instant at) {
        em.createNativeQuery("""
                insert into project_jira_label (project_id, label, updated_at) values (?1, ?2, ?3)
                on conflict (project_id) do update set label = excluded.label, updated_at = excluded.updated_at""")
                .setParameter(1, projectId.toString()).setParameter(2, label.value()).setParameter(3, at.toString())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void delete(UUID projectId) {
        em.createNativeQuery("delete from project_jira_label where project_id = ?1").setParameter(1, projectId.toString())
                .executeUpdate();
    }
}
