package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migration V32 (021) : les relectures terminées sans aucun point, gardées avant que « Terminer » ne
 * les supprime, disparaissent ; les séances avec des points, en cours ou préparées restent.
 */
class MigrationV32IT {

    private static final String AT = "'2026-11-21T09:00:00Z'";

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void baseV31() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        flyway("31").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at)"
                    + " values ('p1', 'Aurora', 'aurora', " + AT + ", " + AT + ")");
            s.executeUpdate("insert into session (id, project_id, name, need, language, origin, state, created_at, updated_at)"
                    + " values ('s1', 'p1', 'Agenda', 'Besoin', 'FR', 'LOCAL', 'QUESTIONING', " + AT + ", " + AT + ")");
            interview(s, "vide", "FINISHED");
            interview(s, "notee", "FINISHED");
            interview(s, "en-cours", "IN_PROGRESS");
            interview(s, "preparee", "PREPARED");
            s.executeUpdate("insert into interview_point (id, interview_id, session_id, origin, text, outcome, created_at,"
                    + " updated_at) values ('pt1', 'notee', 's1', 'REMARK', 'À revoir', 'CHANGE', " + AT + ", " + AT + ")");
        }
        flyway("32").migrate();
    }

    static void interview(Statement s, String id, String state) throws SQLException {
        s.executeUpdate("insert into interview (id, project_id, state, created_at) values ('" + id + "', 'p1', '" + state
                + "', " + AT + ")");
        s.executeUpdate("insert into interview_spec (interview_id, session_id, rank) values ('" + id + "', 's1', 1)");
    }

    Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void lesRelecturesTermineesSansPointDisparaissent() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            List<String> left = new ArrayList<>();
            try (ResultSet r = s.executeQuery("select id from interview order by id")) {
                while (r.next()) {
                    left.add(r.getString(1));
                }
            }
            assertThat(left).containsExactly("en-cours", "notee", "preparee");
            try (ResultSet r = s.executeQuery("select count(*) from interview_spec where interview_id = 'vide'")) {
                r.next();
                assertThat(r.getInt(1)).isZero();
            }
        }
    }
}
