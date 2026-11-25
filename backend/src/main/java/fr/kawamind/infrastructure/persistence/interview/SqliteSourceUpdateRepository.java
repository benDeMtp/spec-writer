package fr.kawamind.infrastructure.persistence.interview;

import fr.kawamind.domain.interview.SourceUpdate;
import fr.kawamind.domain.port.SourceUpdateRepository;
import fr.kawamind.domain.specification.SourceRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;

/** « À reporter » en SQLite (V30), en JDBC : le pilote refuse les paramètres nuls non typés. */
@ApplicationScoped
public class SqliteSourceUpdateRepository implements SourceUpdateRepository {

    private static final String COLUMNS = """
            id, session_id, interview_id, point_id, source_kind, source_ref, source_title, fingerprint, text, state,
            created_at, closed_at""";

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(SourceUpdate u) {
        em.unwrap(Session.class).doWork(c -> {
            try (PreparedStatement s = c.prepareStatement("insert into source_update (" + COLUMNS + """
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (id) do update set state = excluded.state, closed_at = excluded.closed_at""")) {
                s.setString(1, u.id().toString());
                s.setString(2, u.sessionId().toString());
                text(s, 3, u.interviewId() == null ? null : u.interviewId().toString());
                text(s, 4, u.pointId() == null ? null : u.pointId().toString());
                s.setString(5, u.kind().name());
                s.setString(6, u.ref());
                text(s, 7, u.title());
                text(s, 8, u.fingerprint());
                s.setString(9, u.text());
                s.setString(10, u.state().name());
                s.setString(11, u.createdAt().toString());
                text(s, 12, u.closedAt() == null ? null : u.closedAt().toString());
                s.executeUpdate();
            }
        });
    }

    @Override
    @Transactional
    public Optional<SourceUpdate> find(UUID id) {
        return query("select " + COLUMNS + " from source_update where id = ?", id.toString()).stream().findFirst();
    }

    @Override
    @Transactional
    public List<SourceUpdate> open(UUID sessionId) {
        return query("select " + COLUMNS + " from source_update where session_id = ? and state = 'OPEN' order by created_at, id",
                sessionId.toString());
    }

    private List<SourceUpdate> query(String sql, String parameter) {
        List<SourceUpdate> out = new ArrayList<>();
        em.unwrap(Session.class).doWork(c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setString(1, parameter);
                try (ResultSet rs = s.executeQuery()) {
                    while (rs.next()) {
                        out.add(read(rs));
                    }
                }
            }
        });
        return out;
    }

    private static SourceUpdate read(ResultSet rs) throws SQLException {
        return new SourceUpdate(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), uuid(rs.getString(3)),
                uuid(rs.getString(4)), SourceRef.Kind.valueOf(rs.getString(5)), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getString(9), SourceUpdate.State.valueOf(rs.getString(10)),
                Instant.parse(rs.getString(11)), rs.getString(12) == null ? null : Instant.parse(rs.getString(12)));
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static void text(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
