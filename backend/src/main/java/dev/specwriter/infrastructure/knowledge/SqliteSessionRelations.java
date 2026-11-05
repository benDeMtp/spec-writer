package dev.specwriter.infrastructure.knowledge;

import dev.specwriter.domain.port.SessionRelationRepository;
import dev.specwriter.domain.session.SessionRelation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;

/** Relation « relates to » d'une session, dans ses colonnes (V10), en JDBC (paramètres nuls typés). */
@ApplicationScoped
public class SqliteSessionRelations implements SessionRelationRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Optional<SessionRelation> find(UUID sessionId) {
        return em.unwrap(Session.class).doReturningWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select relates_to, relates_to_link from session where id = ? and relates_to is not null")) {
                statement.setString(1, sessionId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    String link = rows.getString(2);
                    return Optional.of(new SessionRelation(sessionId, rows.getString(1),
                            link == null ? null : SessionRelation.Link.valueOf(link)));
                }
            }
        });
    }

    @Override
    @Transactional
    public void save(SessionRelation relation) {
        em.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "update session set relates_to = ?, relates_to_link = ? where id = ?")) {
                statement.setString(1, relation.relatesTo());
                statement.setString(2, relation.link().name());
                statement.setString(3, relation.sessionId().toString());
                statement.executeUpdate();
            }
        });
    }
}
