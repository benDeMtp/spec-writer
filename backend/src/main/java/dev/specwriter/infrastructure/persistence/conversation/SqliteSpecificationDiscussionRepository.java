package dev.specwriter.infrastructure.persistence.conversation;

import dev.specwriter.domain.conversation.SpecificationDiscussion;
import dev.specwriter.domain.port.SpecificationDiscussionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Discussions des spécifications dans la table de la migration V22 (011, research.md R1). */
@ApplicationScoped
public class SqliteSpecificationDiscussionRepository implements SpecificationDiscussionRepository {

    private static final String COLUMNS =
            "select conversation_id, session_id, specification_name, current, created_at from specification_discussion";

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Optional<SpecificationDiscussion> current(UUID sessionId) {
        return rows(COLUMNS + " where session_id = ?1 and current = 1", sessionId.toString()).stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<SpecificationDiscussion> ofConversation(UUID conversationId) {
        return rows(COLUMNS + " where conversation_id = ?1", conversationId.toString()).stream().findFirst();
    }

    @Override
    @Transactional
    public Map<UUID, SpecificationDiscussion> ofConversations(Collection<UUID> conversationIds) {
        Map<UUID, SpecificationDiscussion> out = new LinkedHashMap<>();
        if (conversationIds.isEmpty()) {
            return out;
        }
        Map<String, UUID> wanted = new LinkedHashMap<>();
        conversationIds.forEach(id -> wanted.put(id.toString(), id));
        for (SpecificationDiscussion d : rows(COLUMNS, null)) {
            if (wanted.containsKey(d.conversationId().toString())) {
                out.put(d.conversationId(), d);
            }
        }
        return out;
    }

    @Override
    @Transactional
    public List<SpecificationDiscussion> bySession(UUID sessionId) {
        return rows(COLUMNS + " where session_id = ?1 order by created_at", sessionId.toString());
    }

    @Override
    @Transactional
    public void save(SpecificationDiscussion d) {
        em.createNativeQuery("""
                insert into specification_discussion (conversation_id, session_id, specification_name, current, created_at)
                values (?1, ?2, ?3, ?4, ?5)
                on conflict (conversation_id) do update set session_id = excluded.session_id,
                    specification_name = excluded.specification_name, current = excluded.current""")
                .setParameter(1, d.conversationId().toString())
                .setParameter(2, d.attached() ? d.sessionId().toString() : null)
                .setParameter(3, d.specificationName()).setParameter(4, d.current() ? 1 : 0)
                .setParameter(5, d.createdAt().toString()).executeUpdate();
    }

    @Override
    @Transactional
    public void detachAll(UUID sessionId) {
        em.createNativeQuery("update specification_discussion set session_id = null, current = 0 where session_id = ?1")
                .setParameter(1, sessionId.toString()).executeUpdate();
    }

    @Override
    @Transactional
    public void renameAll(UUID sessionId, String name) {
        String kept = SpecificationDiscussion.name(name);
        em.createNativeQuery("update specification_discussion set specification_name = ?1 where session_id = ?2")
                .setParameter(1, kept).setParameter(2, sessionId.toString()).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<SpecificationDiscussion> rows(String sql, String parameter) {
        var query = em.createNativeQuery(sql);
        if (parameter != null) {
            query.setParameter(1, parameter);
        }
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(r -> new SpecificationDiscussion(UUID.fromString((String) r[0]),
                r[1] == null ? null : UUID.fromString((String) r[1]), (String) r[2], ((Number) r[3]).intValue() != 0,
                Instant.parse((String) r[4]))).toList();
    }
}
