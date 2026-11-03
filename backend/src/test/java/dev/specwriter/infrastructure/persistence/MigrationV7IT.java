package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
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
 * Migration V7 (feature 002, T008, SC-001) : une base V6 avec 3 sessions, leurs questions et leurs
 * versions migre sans perte, et toutes les sessions sont dans le projet par défaut.
 */
class MigrationV7IT {

    static final String DEFAULT_PROJECT = "00000000-0000-0000-0000-000000000001";

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void baseV6() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        flyway("6").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            for (int i = 1; i <= 3; i++) {
                s.executeUpdate("insert into session (id, name, need, language, origin, state, created_at, updated_at) values"
                        + " ('s" + i + "', 'Session " + i + "', 'Les employés doivent réserver une salle numéro " + i
                        + "', 'FR', 'LOCAL', 'GENERATED', '2026-10-0" + i + "T09:00:00Z', '2026-10-0" + i + "T09:00:00Z')");
                s.executeUpdate("insert into question (id, session_id, ordinal, theme, batch, label, rationale, state)"
                        + " values ('q" + i + "', 's" + i + "', 1, 'GOAL', 1, 'Objectif ?', 'Pour cadrer', 'ANSWERED')");
                for (int n = 1; n <= 2; n++) {
                    s.executeUpdate("insert into specification_version (id, session_id, number, title, created_at, origin,"
                            + " content, markdown, input_fingerprint) values ('v" + i + n + "', 's" + i + "', " + n
                            + ", 'Réservation', '2026-10-01T09:00:00Z', 'GENERATED', '{}', 'règle de réservation " + i
                            + "', 'f" + i + n + "')");
                }
            }
        }
    }

    Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void lesSessionsExistantesSontDansLeProjetParDefautSansPerte() throws SQLException {
        flyway("7").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            assertThat(rows(s, "select id, name, name_key from project")).containsExactly(
                    DEFAULT_PROJECT + "|Projet par défaut|projet par défaut");
            assertThat(rows(s, "select id, project_id from session order by id")).containsExactly(
                    "s1|" + DEFAULT_PROJECT, "s2|" + DEFAULT_PROJECT, "s3|" + DEFAULT_PROJECT);
            assertThat(count(s, "select count(*) from question")).isEqualTo(3);
            assertThat(count(s, "select count(*) from specification_version")).isEqualTo(6);
            assertThat(rows(s, "select current_project_id from app_settings")).containsExactly(DEFAULT_PROJECT);
            // la recherche plein texte suit toujours les sessions
            assertThat(count(s, "select count(*) from spec_search where spec_search match 'reservation'")).isEqualTo(3);
        }
    }

    @Test
    void uneSessionDoitAvoirUnProjetExistant() throws SQLException {
        flyway("7").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.executeUpdate("insert into session (id, name, need, language, origin, state,"
                    + " created_at, updated_at) values ('s9', 'n', 'besoin', 'FR', 'LOCAL', 'QUESTIONING', 'x', 'x')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("project_id");
            assertThatThrownBy(() -> s.executeUpdate("update session set project_id = null where id = 's1'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("project_id");
            assertThatThrownBy(() -> s.executeUpdate("update session set project_id = 'inconnu' where id = 's1'"))
                    .isInstanceOf(SQLException.class);
            // un projet qui a encore des sessions ne peut pas être supprimé directement
            assertThatThrownBy(() -> s.executeUpdate("delete from project where id = '" + DEFAULT_PROJECT + "'"))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate("insert into session (id, name, need, language, origin, state, created_at, updated_at,"
                    + " project_id) values ('s9', 'n', 'besoin', 'FR', 'LOCAL', 'QUESTIONING', 'x', 'x', '"
                    + DEFAULT_PROJECT + "')");
            assertThat(count(s, "select count(*) from session")).isEqualTo(4);
        }
    }

    @Test
    void nomUniqueSansTenirCompteDeLaCasseEtLongueursBornees() throws SQLException {
        flyway("7").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at)"
                    + " values ('p2', 'PROJET PAR DÉFAUT', 'projet par défaut', 'x', 'x')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at)"
                    + " values ('p3', '" + "n".repeat(81) + "', 'n81', 'x', 'x')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("insert into project (id, name, name_key, description, created_at,"
                    + " updated_at) values ('p4', 'Paie', 'paie', '" + "d".repeat(1001) + "', 'x', 'x')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("insert into project (id, name, name_key, jira_project_key,"
                    + " created_at, updated_at) values ('p5', 'Paie', 'paie', 'ABCDEFGHIJK', 'x', 'x')"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(Files.exists(dir.resolve("spec-writer.db"))).isTrue();
    }

    static List<String> rows(Statement s, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = s.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(i > 1 ? "|" : "").append(rs.getString(i));
                }
                out.add(row.toString());
            }
        }
        return out;
    }

    static long count(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
