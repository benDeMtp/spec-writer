package dev.specwriter.infrastructure.knowledge;

import dev.specwriter.domain.knowledge.KnowledgeSource;
import dev.specwriter.domain.port.KnowledgeSourceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import org.hibernate.Session;
import java.util.List;
import java.util.UUID;

/** Repères de l'index, dans les colonnes du projet (V10) ; l'entité du projet ne les écrase pas. */
@ApplicationScoped
public class SqliteKnowledgeSources implements KnowledgeSourceRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public KnowledgeSource find(UUID projectId) {
        List<Object[]> rows = em.createNativeQuery("""
                select architecture_workspace, architecture_ownership, architecture_hash, jira_sync_mark,
                       knowledge_synced_at
                from project where id = ?1""").setParameter(1, projectId.toString()).getResultList();
        return rows.stream().findFirst()
                .map(r -> new KnowledgeSource(projectId, (String) r[0], (String) r[1], (String) r[2], instant(r[3]),
                        instant(r[4])))
                .orElseGet(() -> KnowledgeSource.empty(projectId));
    }

    /** En JDBC : le pilote SQLite refuse un paramètre nul que Hibernate lie sans type. */
    @Override
    @Transactional
    public void save(KnowledgeSource s) {
        em.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    update project set architecture_workspace = ?, architecture_ownership = ?, architecture_hash = ?,
                                       jira_sync_mark = ?, knowledge_synced_at = ?
                    where id = ?""")) {
                text(statement, 1, s.workspace());
                text(statement, 2, s.ownership());
                text(statement, 3, s.architectureHash());
                text(statement, 4, text(s.jiraMark()));
                text(statement, 5, text(s.syncedAt()));
                statement.setString(6, s.projectId().toString());
                statement.executeUpdate();
            }
        });
    }

    private static void text(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static Instant instant(Object value) {
        return value == null ? null : Instant.parse((String) value);
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
