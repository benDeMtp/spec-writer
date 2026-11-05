package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T038 : V12 initialise le compteur des versions à {@code max(number) + 1}, et la suppression d'une
 * version met la recherche à jour avec la dernière version restante (FR-014, FR-018 de 002).
 */
class MigrationVersionNumberingIT {

    static final String WITH = "11111111-1111-1111-1111-111111111111";
    static final String WITHOUT = "22222222-2222-2222-2222-222222222222";

    @TempDir
    Path dir;

    void session(Connection c, String id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                insert into session (id, name, need, language, origin, state, created_at, updated_at, project_id)
                values (?, 'Avoirs', 'Saisir un avoir', 'FR', 'LOCAL', 'GENERATED', '2026-10-01T08:00:00Z',
                  '2026-10-01T08:00:00Z', '00000000-0000-0000-0000-000000000001')""")) {
            p.setString(1, id);
            p.executeUpdate();
        }
    }

    void version(Connection c, String session, int number, String markdown) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                insert into specification_version (id, session_id, number, title, input_fingerprint, origin, content,
                  markdown, created_at) values (?, ?, ?, 'Avoirs', 'fp', 'GENERATED', '{}', ?, '2026-10-01T08:00:00Z')""")) {
            p.setString(1, session + "-" + number);
            p.setString(2, session);
            p.setInt(3, number);
            p.setString(4, markdown);
            p.executeUpdate();
        }
    }

    static String single(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    @Test
    void compteurInitialiseEtRechercheTenueAJour() throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("spec-writer.db");
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target("11").load().migrate();
        try (Connection c = DriverManager.getConnection(url)) {
            session(c, WITH);
            session(c, WITHOUT);
            version(c, WITH, 1, "premiere");
            version(c, WITH, 3, "troisieme");
        }

        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").load().migrate();

        try (Connection c = DriverManager.getConnection(url)) {
            assertThat(single(c, "select next_version_number from session where id = '" + WITH + "'")).isEqualTo("4");
            assertThat(single(c, "select next_version_number from session where id = '" + WITHOUT + "'")).isEqualTo("1");
            assertThat(single(c, "select markdown from spec_search where session_id = '" + WITH + "'")).isEqualTo("troisieme");
            try (Statement s = c.createStatement()) {
                s.executeUpdate("delete from specification_version where session_id = '" + WITH + "' and number = 3");
            }
            assertThat(single(c, "select markdown from spec_search where session_id = '" + WITH + "'")).isEqualTo("premiere");
            try (Statement s = c.createStatement()) {
                s.executeUpdate("delete from specification_version where session_id = '" + WITH + "' and number = 1");
            }
            assertThat(single(c, "select markdown from spec_search where session_id = '" + WITH + "'")).isEmpty();
            assertThat(single(c, "select next_version_number from session where id = '" + WITH + "'")).isEqualTo("4");
        }
    }
}
