package dev.specwriter.infrastructure.persistence.specification;

import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@ApplicationScoped
public class SpecificationJpaRepository implements SpecificationRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(SpecificationVersion v) {
        SpecificationVersionEntity e = new SpecificationVersionEntity();
        e.id = v.id().toString();
        e.sessionId = v.sessionId().toString();
        e.number = v.number();
        e.title = v.content().title();
        e.inputFingerprint = v.inputFingerprint();
        e.origin = v.origin().name();
        e.content = JSON.writeValueAsString(v.content());
        e.markdown = v.markdown();
        e.createdAt = v.createdAt();
        e.contextHash = v.contextHash() == null ? "" : v.contextHash();
        e.contextUpdatedAt = v.contextUpdatedAt();
        // persist et non merge : une version existante n'est jamais réécrite
        em.persist(e);
    }

    @Override
    @Transactional
    public Optional<SpecificationVersion> find(UUID sessionId, int number) {
        return query("select v from SpecificationVersionEntity v where v.sessionId = :s and v.number = :n", sessionId)
                .setParameter("n", number).getResultStream().findFirst().map(SpecificationJpaRepository::toDomain);
    }

    @Override
    @Transactional
    public Optional<SpecificationVersion> findByFingerprint(UUID sessionId, String fingerprint) {
        return query("select v from SpecificationVersionEntity v where v.sessionId = :s and v.inputFingerprint = :f"
                + " order by v.number desc", sessionId)
                .setParameter("f", fingerprint).setMaxResults(1).getResultStream().findFirst()
                .map(SpecificationJpaRepository::toDomain);
    }

    @Override
    @Transactional
    public List<SpecificationVersion> list(UUID sessionId) {
        return query("select v from SpecificationVersionEntity v where v.sessionId = :s order by v.number", sessionId)
                .getResultList().stream().map(SpecificationJpaRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteAll(UUID sessionId) {
        em.createQuery("delete from SpecificationVersionEntity v where v.sessionId = :s")
                .setParameter("s", sessionId.toString()).executeUpdate();
    }

    @Override
    @Transactional
    public int lastNumber(UUID sessionId) {
        Integer max = em.createQuery("select max(v.number) from SpecificationVersionEntity v where v.sessionId = :s",
                Integer.class).setParameter("s", sessionId.toString()).getSingleResult();
        return max == null ? 0 : max;
    }

    private jakarta.persistence.TypedQuery<SpecificationVersionEntity> query(String jpql, UUID sessionId) {
        return em.createQuery(jpql, SpecificationVersionEntity.class).setParameter("s", sessionId.toString());
    }

    private static SpecificationVersion toDomain(SpecificationVersionEntity e) {
        return new SpecificationVersion(UUID.fromString(e.id), UUID.fromString(e.sessionId), e.number,
                JSON.readValue(e.content, SpecificationContent.class), e.inputFingerprint, VersionOrigin.valueOf(e.origin),
                e.markdown, e.createdAt, e.contextHash, e.contextUpdatedAt);
    }
}
