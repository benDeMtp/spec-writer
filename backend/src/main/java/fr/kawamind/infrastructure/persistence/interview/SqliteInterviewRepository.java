package fr.kawamind.infrastructure.persistence.interview;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.InterviewSpec;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.specification.Anchor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tables {@code interview}, {@code interview_spec} et {@code interview_point} (V30, 021). En JDBC :
 * le pilote SQLite refuse un paramètre nul que Hibernate lie sans type, et la plupart des colonnes
 * sont facultatives.
 */
@ApplicationScoped
public class SqliteInterviewRepository implements InterviewRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final String INTERVIEW_COLUMNS = "id, project_id, state, participants, created_at, started_at, "
            + "finished_at, cursor_session_id, cursor_element, cursor_step, cursor_alternative";
    private static final String POINT_COLUMNS = "id, interview_id, session_id, anchor_element, anchor_step, "
            + "anchor_alternative, origin, origin_code, text, outcome, owner, due, source_kind, source_ref, created_at, "
            + "updated_at";

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(Interview interview, List<InterviewSpec> specs) {
        work(c -> {
            upsert(c, interview);
            try (PreparedStatement delete = c.prepareStatement("delete from interview_spec where interview_id = ?")) {
                delete.setString(1, interview.id().toString());
                delete.executeUpdate();
            }
            for (InterviewSpec spec : specs) {
                try (PreparedStatement insert = c.prepareStatement("""
                        insert into interview_spec (interview_id, session_id, rank, version, excluded, in_progress,
                                                    applied_version)
                        values (?, ?, ?, ?, ?, ?, ?)""")) {
                    insert.setString(1, interview.id().toString());
                    insert.setString(2, spec.sessionId().toString());
                    insert.setInt(3, spec.rank());
                    integer(insert, 4, spec.version());
                    insert.setString(5, JSON.writeValueAsString(spec.excluded().stream().sorted().toList()));
                    insert.setInt(6, interview.state() == Interview.State.IN_PROGRESS ? 1 : 0);
                    integer(insert, 7, spec.appliedVersion());
                    insert.executeUpdate();
                }
            }
        });
    }

    @Override
    @Transactional
    public void save(Interview interview) {
        work(c -> {
            upsert(c, interview);
            try (PreparedStatement update = c.prepareStatement(
                    "update interview_spec set in_progress = ? where interview_id = ?")) {
                update.setInt(1, interview.state() == Interview.State.IN_PROGRESS ? 1 : 0);
                update.setString(2, interview.id().toString());
                update.executeUpdate();
            }
        });
    }

    private static void upsert(Connection c, Interview i) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("insert into interview (" + INTERVIEW_COLUMNS + ") "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (id) do update set state = excluded.state, "
                + "participants = excluded.participants, started_at = excluded.started_at, "
                + "finished_at = excluded.finished_at, cursor_session_id = excluded.cursor_session_id, "
                + "cursor_element = excluded.cursor_element, cursor_step = excluded.cursor_step, "
                + "cursor_alternative = excluded.cursor_alternative")) {
            s.setString(1, i.id().toString());
            s.setString(2, i.projectId().toString());
            s.setString(3, i.state().name());
            s.setString(4, JSON.writeValueAsString(i.participants()));
            s.setString(5, i.createdAt().toString());
            text(s, 6, i.startedAt() == null ? null : i.startedAt().toString());
            text(s, 7, i.finishedAt() == null ? null : i.finishedAt().toString());
            Interview.Cursor cursor = i.cursor();
            Anchor at = cursor == null ? null : cursor.anchor();
            text(s, 8, cursor == null ? null : cursor.sessionId().toString());
            text(s, 9, at == null ? null : at.element());
            integer(s, 10, at == null ? null : at.step());
            s.setInt(11, at != null && at.alternative() ? 1 : 0);
            s.executeUpdate();
        }
    }

    @Override
    @Transactional
    public Optional<Interview> find(UUID id) {
        return query("select " + INTERVIEW_COLUMNS + " from interview where id = ?", List.of(id.toString()),
                SqliteInterviewRepository::interview).stream().findFirst();
    }

    @Override
    @Transactional
    public List<InterviewSpec> specs(UUID interviewId) {
        return query("""
                select interview_id, session_id, rank, version, excluded, applied_version
                from interview_spec where interview_id = ? order by rank""", List.of(interviewId.toString()),
                rs -> new InterviewSpec(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getInt(3),
                        integer(rs, 4), new LinkedHashSet<>(JSON.readValue(rs.getString(5), STRINGS)), integer(rs, 6)));
    }

    @Override
    @Transactional
    public void saveSpec(InterviewSpec spec) {
        work(c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    update interview_spec set rank = ?, version = ?, excluded = ?, applied_version = ?
                    where interview_id = ? and session_id = ?""")) {
                s.setInt(1, spec.rank());
                integer(s, 2, spec.version());
                s.setString(3, JSON.writeValueAsString(spec.excluded().stream().sorted().toList()));
                integer(s, 4, spec.appliedVersion());
                s.setString(5, spec.interviewId().toString());
                s.setString(6, spec.sessionId().toString());
                s.executeUpdate();
            }
        });
    }

    @Override
    @Transactional
    public List<Interview> byState(UUID projectId, Interview.State state) {
        return query("select " + INTERVIEW_COLUMNS + " from interview where project_id = ? and state = ? "
                + "order by created_at desc", List.of(projectId.toString(), state.name()), SqliteInterviewRepository::interview);
    }

    @Override
    @Transactional
    public List<Interview> forSession(UUID sessionId) {
        return query("select " + prefixed() + " from interview i join interview_spec s on s.interview_id = i.id "
                + "where s.session_id = ? order by i.created_at desc", List.of(sessionId.toString()),
                SqliteInterviewRepository::interview);
    }

    private static String prefixed() {
        StringBuilder b = new StringBuilder();
        for (String column : INTERVIEW_COLUMNS.split(", ")) {
            b.append(b.isEmpty() ? "" : ", ").append("i.").append(column);
        }
        return b.toString();
    }

    @Override
    @Transactional
    public Optional<UUID> inProgress(UUID sessionId) {
        return query("select interview_id from interview_spec where session_id = ? and in_progress = 1",
                List.of(sessionId.toString()), rs -> UUID.fromString(rs.getString(1))).stream().findFirst();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        work(c -> {
            try (PreparedStatement s = c.prepareStatement("delete from interview where id = ?")) {
                s.setString(1, id.toString());
                s.executeUpdate();
            }
        });
    }

    @Override
    @Transactional
    public void savePoint(InterviewPoint p) {
        work(c -> {
            try (PreparedStatement s = c.prepareStatement("insert into interview_point (" + POINT_COLUMNS + ") "
                    + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (id) do update set "
                    + "anchor_element = excluded.anchor_element, anchor_step = excluded.anchor_step, "
                    + "anchor_alternative = excluded.anchor_alternative, text = excluded.text, "
                    + "outcome = excluded.outcome, owner = excluded.owner, due = excluded.due, "
                    + "source_kind = excluded.source_kind, source_ref = excluded.source_ref, "
                    + "updated_at = excluded.updated_at")) {
                s.setString(1, p.id().toString());
                s.setString(2, p.interviewId().toString());
                s.setString(3, p.sessionId().toString());
                text(s, 4, p.anchor() == null ? null : p.anchor().element());
                integer(s, 5, p.anchor() == null ? null : p.anchor().step());
                s.setInt(6, p.anchor() != null && p.anchor().alternative() ? 1 : 0);
                s.setString(7, p.origin().name());
                text(s, 8, p.originCode());
                s.setString(9, p.text());
                text(s, 10, p.outcome() == null ? null : p.outcome().name());
                text(s, 11, p.owner());
                text(s, 12, p.due() == null ? null : p.due().toString());
                text(s, 13, p.source() == null ? null : p.source().kind());
                text(s, 14, p.source() == null ? null : p.source().ref());
                s.setString(15, p.createdAt().toString());
                s.setString(16, p.updatedAt().toString());
                s.executeUpdate();
            }
        });
    }

    @Override
    @Transactional
    public Optional<InterviewPoint> point(UUID id) {
        return query("select " + POINT_COLUMNS + " from interview_point where id = ?", List.of(id.toString()),
                SqliteInterviewRepository::point).stream().findFirst();
    }

    @Override
    @Transactional
    public List<InterviewPoint> points(UUID interviewId) {
        return query("select " + POINT_COLUMNS + " from interview_point where interview_id = ? order by created_at, id",
                List.of(interviewId.toString()), SqliteInterviewRepository::point);
    }

    @Override
    @Transactional
    public int countPoints(UUID interviewId, UUID sessionId) {
        return query("select count(*) from interview_point where interview_id = ? and session_id = ?",
                List.of(interviewId.toString(), sessionId.toString()), rs -> rs.getInt(1)).getFirst();
    }

    @Override
    @Transactional
    public void deletePoint(UUID id) {
        work(c -> {
            try (PreparedStatement s = c.prepareStatement("delete from interview_point where id = ?")) {
                s.setString(1, id.toString());
                s.executeUpdate();
            }
        });
    }

    private static Interview interview(ResultSet rs) throws SQLException {
        String cursorSession = rs.getString(8);
        Interview.Cursor cursor = cursorSession == null ? null
                : new Interview.Cursor(UUID.fromString(cursorSession),
                        rs.getString(9) == null ? null : new Anchor(rs.getString(9), integer(rs, 10), rs.getInt(11) == 1));
        return new Interview(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                Interview.State.valueOf(rs.getString(3)), JSON.readValue(rs.getString(4), STRINGS),
                Instant.parse(rs.getString(5)), instant(rs.getString(6)), instant(rs.getString(7)), cursor);
    }

    private static InterviewPoint point(ResultSet rs) throws SQLException {
        String element = rs.getString(4);
        String sourceKind = rs.getString(13);
        return new InterviewPoint(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)), element == null ? null : new Anchor(element, integer(rs, 5), rs.getInt(6) == 1),
                InterviewPoint.Origin.valueOf(rs.getString(7)), rs.getString(8), rs.getString(9),
                rs.getString(10) == null ? null : InterviewPoint.Outcome.valueOf(rs.getString(10)), rs.getString(11),
                rs.getString(12) == null ? null : LocalDate.parse(rs.getString(12)),
                sourceKind == null ? null : new InterviewPoint.DesignatedSource(sourceKind, rs.getString(14)),
                Instant.parse(rs.getString(15)), Instant.parse(rs.getString(16)));
    }

    @FunctionalInterface
    interface Row<T> {
        T read(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    interface Work {
        void run(Connection c) throws SQLException;
    }

    private <T> List<T> query(String sql, List<String> parameters, Row<T> row) {
        List<T> out = new ArrayList<>();
        work(c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                for (int i = 0; i < parameters.size(); i++) {
                    s.setString(i + 1, parameters.get(i));
                }
                try (ResultSet rs = s.executeQuery()) {
                    while (rs.next()) {
                        out.add(row.read(rs));
                    }
                }
            }
        });
        return out;
    }

    private void work(Work work) {
        try {
            em.unwrap(Session.class).doWork(work::run);
        } catch (RuntimeException e) {
            if (busy(e)) {
                throw new DomainException(ErrorCode.INTERVIEW_BUSY);
            }
            throw e;
        }
    }

    /** L'index unique partiel (V30) refuse une seconde séance en cours sur la même spécification. */
    private static boolean busy(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains("interview_spec.session_id")) {
                return true;
            }
        }
        return false;
    }

    private static void text(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void integer(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static Integer integer(ResultSet rs, int index) throws SQLException {
        int value = rs.getInt(index);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
