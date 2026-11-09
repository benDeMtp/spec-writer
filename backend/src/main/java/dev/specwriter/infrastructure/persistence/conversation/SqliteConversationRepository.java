package dev.specwriter.infrastructure.persistence.conversation;

import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.port.ConversationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Conversations dans les tables de la migration V18 (010, research.md R6). */
@ApplicationScoped
public class SqliteConversationRepository implements ConversationRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Conversation> list(UUID projectId) {
        List<Object[]> rows = em.createNativeQuery("""
                select id, project_id, name, created_at, updated_at from conversation
                where project_id = ?1 order by updated_at desc, created_at desc""")
                .setParameter(1, projectId.toString()).getResultList();
        return rows.stream().map(SqliteConversationRepository::conversation).toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<Conversation> find(UUID id) {
        List<Object[]> rows = em.createNativeQuery(
                "select id, project_id, name, created_at, updated_at from conversation where id = ?1")
                .setParameter(1, id.toString()).getResultList();
        return rows.stream().findFirst().map(SqliteConversationRepository::conversation);
    }

    @Override
    @Transactional
    public void save(Conversation c) {
        em.createNativeQuery("""
                insert into conversation (id, project_id, name, created_at, updated_at) values (?1, ?2, ?3, ?4, ?5)
                on conflict (id) do update set name = excluded.name, updated_at = excluded.updated_at""")
                .setParameter(1, c.id().toString()).setParameter(2, c.projectId().toString())
                .setParameter(3, c.name()).setParameter(4, c.createdAt().toString())
                .setParameter(5, c.updatedAt().toString()).executeUpdate();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        em.createNativeQuery("delete from conversation where id = ?1").setParameter(1, id.toString()).executeUpdate();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<ConversationMessage> messages(UUID conversationId) {
        List<Object[]> rows = em.createNativeQuery("""
                select id, ordinal, role, text, state, history_truncated, created_at from conversation_message
                where conversation_id = ?1 order by ordinal""")
                .setParameter(1, conversationId.toString()).getResultList();
        return rows.stream().map(r -> new ConversationMessage(UUID.fromString((String) r[0]), conversationId,
                ((Number) r[1]).intValue(), MessageRole.valueOf((String) r[2]), (String) r[3],
                MessageState.valueOf((String) r[4]), ((Number) r[5]).intValue() != 0, Instant.parse((String) r[6])))
                .toList();
    }

    @Override
    @Transactional
    public void saveMessage(ConversationMessage m) {
        em.createNativeQuery("""
                insert into conversation_message (id, conversation_id, ordinal, role, text, state, history_truncated,
                    created_at) values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                on conflict (id) do update set text = excluded.text, state = excluded.state,
                    history_truncated = excluded.history_truncated""")
                .setParameter(1, m.id().toString()).setParameter(2, m.conversationId().toString())
                .setParameter(3, m.ordinal()).setParameter(4, m.role().name()).setParameter(5, m.text())
                .setParameter(6, m.state().name()).setParameter(7, m.historyTruncated() ? 1 : 0)
                .setParameter(8, m.createdAt().toString()).executeUpdate();
    }

    @Override
    @Transactional
    public void saveStep(UUID messageId, SearchStep s) {
        em.createNativeQuery("""
                insert into conversation_step (message_id, ordinal, query, kinds, hits) values (?1, ?2, ?3, ?4, ?5)
                on conflict (message_id, ordinal) do update set query = excluded.query, kinds = excluded.kinds,
                    hits = excluded.hits""")
                .setParameter(1, messageId.toString()).setParameter(2, s.ordinal()).setParameter(3, s.query())
                .setParameter(4, s.kinds().stream().map(Enum::name).sorted().collect(Collectors.joining(",")))
                .setParameter(5, s.hits()).executeUpdate();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<SearchStep> steps(UUID messageId) {
        List<Object[]> rows = em.createNativeQuery(
                "select ordinal, query, kinds, hits from conversation_step where message_id = ?1 order by ordinal")
                .setParameter(1, messageId.toString()).getResultList();
        return rows.stream().map(r -> new SearchStep(((Number) r[0]).intValue(), (String) r[1], kinds((String) r[2]),
                ((Number) r[3]).intValue())).toList();
    }

    @Override
    @Transactional
    public void saveSources(UUID messageId, List<CitedSource> sources) {
        em.createNativeQuery("delete from conversation_source where message_id = ?1")
                .setParameter(1, messageId.toString()).executeUpdate();
        for (CitedSource s : sources) {
            em.createNativeQuery(
                    "insert into conversation_source (message_id, n, kind, ref, title) values (?1, ?2, ?3, ?4, ?5)")
                    .setParameter(1, messageId.toString()).setParameter(2, s.n()).setParameter(3, s.kind().name())
                    .setParameter(4, s.ref()).setParameter(5, s.title()).executeUpdate();
        }
    }

    @Override
    @Transactional
    public void clearTurn(UUID messageId) {
        em.createNativeQuery("delete from conversation_step where message_id = ?1")
                .setParameter(1, messageId.toString()).executeUpdate();
        em.createNativeQuery("delete from conversation_source where message_id = ?1")
                .setParameter(1, messageId.toString()).executeUpdate();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<CitedSource> sources(UUID messageId) {
        List<Object[]> rows = em.createNativeQuery(
                "select n, kind, ref, title from conversation_source where message_id = ?1 order by rowid")
                .setParameter(1, messageId.toString()).getResultList();
        return rows.stream().map(r -> new CitedSource(((Number) r[0]).intValue(), SourceKind.valueOf((String) r[1]),
                (String) r[2], (String) r[3])).toList();
    }

    @Override
    @Transactional
    public void link(UUID conversationId, UUID sessionId) {
        em.createNativeQuery("insert or ignore into conversation_session (conversation_id, session_id) values (?1, ?2)")
                .setParameter(1, conversationId.toString()).setParameter(2, sessionId.toString()).executeUpdate();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<UUID> sessions(UUID conversationId) {
        List<String> rows = em.createNativeQuery(
                "select session_id from conversation_session where conversation_id = ?1 order by rowid")
                .setParameter(1, conversationId.toString()).getResultList();
        return rows.stream().map(UUID::fromString).toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<UUID> conversationOf(UUID sessionId) {
        List<String> rows = em.createNativeQuery(
                "select conversation_id from conversation_session where session_id = ?1")
                .setParameter(1, sessionId.toString()).getResultList();
        return rows.stream().findFirst().map(UUID::fromString);
    }

    @Override
    @Transactional
    public int interruptRunning() {
        return em.createNativeQuery("update conversation_message set state = 'INCOMPLETE' where state = 'RUNNING'")
                .executeUpdate();
    }

    private static Conversation conversation(Object[] r) {
        return new Conversation(UUID.fromString((String) r[0]), UUID.fromString((String) r[1]), (String) r[2],
                Instant.parse((String) r[3]), Instant.parse((String) r[4]));
    }

    private static java.util.Set<SourceKind> kinds(String raw) {
        return raw == null || raw.isBlank() ? java.util.Set.of()
                : Arrays.stream(raw.split(",")).map(SourceKind::valueOf).collect(Collectors.toSet());
    }
}
