package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.domain.port.SourceRootRepository;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.sources.SourceRoot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Racines de sources en base (013) ; aucun contenu des sources n'y est écrit. */
@ApplicationScoped
public class SourceRootJpaRepository implements SourceRootRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public SourceRoot save(SourceRoot r) {
        SourceRootEntity e = new SourceRootEntity();
        e.id = r.id().toString();
        e.projectId = r.projectId().toString();
        e.location = r.location();
        e.exclusions = r.exclusions().isEmpty() ? null : JSON.writeValueAsString(r.exclusions().patterns());
        e.createdAt = r.createdAt();
        em.merge(e);
        return r;
    }

    @Override
    @Transactional
    public Optional<SourceRoot> find(UUID projectId, UUID rootId) {
        return Optional.ofNullable(em.find(SourceRootEntity.class, rootId.toString()))
                .filter(e -> e.projectId.equals(projectId.toString())).map(SourceRootJpaRepository::toDomain);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<SourceRoot> list(UUID projectId) {
        List<SourceRootEntity> rows = em.createNativeQuery(
                        "select * from project_source_root where project_id = ?1 order by rowid", SourceRootEntity.class)
                .setParameter(1, projectId.toString()).getResultList();
        return rows.stream().map(SourceRootJpaRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID projectId, UUID rootId) {
        SourceRootEntity e = em.find(SourceRootEntity.class, rootId.toString());
        if (e != null && e.projectId.equals(projectId.toString())) {
            em.remove(e);
        }
    }

    private static SourceRoot toDomain(SourceRootEntity e) {
        ExclusionPatterns exclusions = e.exclusions == null ? ExclusionPatterns.NONE
                : ExclusionPatterns.of(List.of(JSON.readValue(e.exclusions, String[].class)));
        return new SourceRoot(UUID.fromString(e.id), UUID.fromString(e.projectId), e.location, exclusions, e.createdAt);
    }
}
