package dev.specwriter.infrastructure.persistence.specification;

import dev.specwriter.domain.knowledge.KnowledgeSelection;
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
        // une version issue de la discussion garde AI_EDIT en base (CHECK de V4) et un lien (V22)
        e.origin = v.origin() == VersionOrigin.DISCUSSION ? VersionOrigin.AI_EDIT.name() : v.origin().name();
        e.content = JSON.writeValueAsString(v.content());
        e.markdown = v.markdown();
        e.createdAt = v.createdAt();
        e.contextHash = v.contextHash() == null ? "" : v.contextHash();
        e.contextUpdatedAt = v.contextUpdatedAt();
        e.knowledge = v.knowledge().equals(KnowledgeSelection.EMPTY) ? null : JSON.writeValueAsString(v.knowledge());
        // persist et non merge : une version existante n'est jamais réécrite
        em.persist(e);
        if (v.origin() == VersionOrigin.DISCUSSION) {
            em.createNativeQuery("insert into version_discussion (version_id, conversation_id) values (?1, null)")
                    .setParameter(1, e.id).executeUpdate();
        }
        // le compteur de la session n'est jamais décrémenté : un numéro supprimé n'est pas réattribué
        em.createNativeQuery("update session set next_version_number = max(next_version_number, ?1) where id = ?2")
                .setParameter(1, v.number() + 1).setParameter(2, v.sessionId().toString()).executeUpdate();
    }

    @Override
    @Transactional
    public Optional<SpecificationVersion> find(UUID sessionId, int number) {
        return query("select v from SpecificationVersionEntity v where v.sessionId = :s and v.number = :n", sessionId)
                .setParameter("n", number).getResultStream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<SpecificationVersion> findByFingerprint(UUID sessionId, String fingerprint) {
        return query("select v from SpecificationVersionEntity v where v.sessionId = :s and v.inputFingerprint = :f"
                + " order by v.number desc", sessionId)
                .setParameter("f", fingerprint).setMaxResults(1).getResultStream().findFirst()
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public List<SpecificationVersion> list(UUID sessionId) {
        return query("select v from SpecificationVersionEntity v where v.sessionId = :s order by v.number", sessionId)
                .getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteAll(UUID sessionId) {
        em.createQuery("delete from SpecificationVersionEntity v where v.sessionId = :s")
                .setParameter("s", sessionId.toString()).executeUpdate();
    }

    @Override
    @Transactional
    public void delete(UUID sessionId, int number) {
        em.createQuery("delete from SpecificationVersionEntity v where v.sessionId = :s and v.number = :n")
                .setParameter("s", sessionId.toString()).setParameter("n", number).executeUpdate();
    }

    @Override
    @Transactional
    public int nextNumber(UUID sessionId) {
        List<?> next = em.createNativeQuery("select next_version_number from session where id = ?1")
                .setParameter(1, sessionId.toString()).getResultList();
        int counter = next.isEmpty() ? 1 : ((Number) next.get(0)).intValue();
        return Math.max(counter, lastNumber(sessionId) + 1);
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

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<UUID> discussionOf(UUID versionId) {
        List<Object> rows = em.createNativeQuery("select conversation_id from version_discussion where version_id = ?1")
                .setParameter(1, versionId.toString()).getResultList();
        return rows.stream().filter(java.util.Objects::nonNull).findFirst().map(r -> UUID.fromString((String) r));
    }

    @Override
    @Transactional
    public void linkDiscussion(UUID versionId, UUID conversationId) {
        em.createNativeQuery("""
                insert into version_discussion (version_id, conversation_id) values (?1, ?2)
                on conflict (version_id) do update set conversation_id = excluded.conversation_id""")
                .setParameter(1, versionId.toString()).setParameter(2, conversationId.toString()).executeUpdate();
    }

    private boolean fromDiscussion(String versionId) {
        return !em.createNativeQuery("select 1 from version_discussion where version_id = ?1")
                .setParameter(1, versionId).getResultList().isEmpty();
    }

    private SpecificationVersion toDomain(SpecificationVersionEntity e) {
        VersionOrigin origin = VersionOrigin.valueOf(e.origin);
        if (origin == VersionOrigin.AI_EDIT && fromDiscussion(e.id)) {
            origin = VersionOrigin.DISCUSSION;
        }
        return new SpecificationVersion(UUID.fromString(e.id), UUID.fromString(e.sessionId), e.number,
                JSON.readValue(e.content, SpecificationContent.class), e.inputFingerprint, origin,
                e.markdown, e.createdAt, e.contextHash, e.contextUpdatedAt,
                e.knowledge == null ? KnowledgeSelection.EMPTY : JSON.readValue(e.knowledge, KnowledgeSelection.class));
    }
}
