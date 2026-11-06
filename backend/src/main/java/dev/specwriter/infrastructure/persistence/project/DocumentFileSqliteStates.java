package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.domain.port.DocumentFileStates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Table {@code document_file} (V15) : chemin, taille, date ; supprimée avec sa source. */
@ApplicationScoped
public class DocumentFileSqliteStates implements DocumentFileStates {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, FileState> of(UUID sourceId) {
        List<Object[]> rows = em.createNativeQuery(
                        "select path, size, modified_at from document_file where source_id = ?1 order by path")
                .setParameter(1, sourceId.toString()).getResultList();
        Map<String, FileState> out = new LinkedHashMap<>();
        for (Object[] r : rows) {
            out.put((String) r[0], new FileState(((Number) r[1]).longValue(), Instant.parse((String) r[2])));
        }
        return out;
    }

    @Override
    @Transactional
    public void replace(UUID sourceId, Map<String, FileState> states) {
        em.createNativeQuery("delete from document_file where source_id = ?1").setParameter(1, sourceId.toString())
                .executeUpdate();
        for (Map.Entry<String, FileState> e : states.entrySet()) {
            em.createNativeQuery("insert into document_file (source_id, path, size, modified_at) values (?1, ?2, ?3, ?4)")
                    .setParameter(1, sourceId.toString()).setParameter(2, e.getKey())
                    .setParameter(3, e.getValue().size()).setParameter(4, e.getValue().modifiedAt().toString())
                    .executeUpdate();
        }
    }
}
