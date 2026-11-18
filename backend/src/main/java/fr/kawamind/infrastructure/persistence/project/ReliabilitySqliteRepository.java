package fr.kawamind.infrastructure.persistence.project;

import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.knowledge.ReliabilitySettings;
import fr.kawamind.domain.knowledge.SourceOrigin;
import fr.kawamind.domain.port.ReliabilityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Table {@code project_source_reliability} (V27) : une ligne par niveau qui diffère du défaut. */
@ApplicationScoped
public class ReliabilitySqliteRepository implements ReliabilityRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public ReliabilitySettings find(UUID projectId) {
        List<Object[]> rows = em.createNativeQuery(
                        "select origin, level from project_source_reliability where project_id = ?1")
                .setParameter(1, projectId.toString()).getResultList();
        Map<SourceOrigin, Reliability> levels = new EnumMap<>(SourceOrigin.class);
        for (Object[] r : rows) {
            levels.put(SourceOrigin.valueOf((String) r[0]), Reliability.valueOf((String) r[1]));
        }
        return new ReliabilitySettings(projectId, levels);
    }

    @Override
    @Transactional
    public void save(ReliabilitySettings settings) {
        String project = settings.projectId().toString();
        em.createNativeQuery("delete from project_source_reliability where project_id = ?1").setParameter(1, project)
                .executeUpdate();
        settings.overrides().forEach((origin, level) -> em.createNativeQuery(
                        "insert into project_source_reliability (project_id, origin, level) values (?1, ?2, ?3)")
                .setParameter(1, project).setParameter(2, origin.name()).setParameter(3, level.name()).executeUpdate());
    }
}
