package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.project.DocSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@ApplicationScoped
public class DocSourceJpaRepository implements DocSourceRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(DocSource s) {
        DocSourceEntity e = new DocSourceEntity();
        e.id = s.id().toString();
        e.projectId = s.projectId().toString();
        e.kind = s.kind().name();
        e.location = s.location();
        e.lastReadAt = s.lastReadAt();
        e.lastResult = s.lastResult() == null ? null : JSON.writeValueAsString(s.lastResult());
        em.merge(e);
    }

    @Override
    @Transactional
    public Optional<DocSource> find(UUID id) {
        return Optional.ofNullable(em.find(DocSourceEntity.class, id.toString())).map(DocSourceJpaRepository::toDomain);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<DocSource> list(UUID projectId) {
        List<DocSourceEntity> rows = em.createNativeQuery(
                        "select * from project_doc_source where project_id = ?1 order by rowid", DocSourceEntity.class)
                .setParameter(1, projectId.toString()).getResultList();
        return rows.stream().map(DocSourceJpaRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        DocSourceEntity e = em.find(DocSourceEntity.class, id.toString());
        if (e != null) {
            em.remove(e);
        }
    }

    private static DocSource toDomain(DocSourceEntity e) {
        DocSource.ReadSummary result = e.lastResult == null ? null : JSON.readValue(e.lastResult, DocSource.ReadSummary.class);
        return new DocSource(UUID.fromString(e.id), UUID.fromString(e.projectId), DocSource.Kind.valueOf(e.kind), e.location,
                e.lastReadAt, result);
    }
}
