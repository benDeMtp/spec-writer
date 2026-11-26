package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Migration V36 (024, data-model) : label du projet, labels des tickets, lien Jira perdu. */
class MigrationV36IT {

    private static final String AT = "'2026-11-26T09:00:00Z'";

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void baseV36() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target("36").load()
                .migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at)"
                    + " values ('p1', 'Aurora', 'aurora', " + AT + ", " + AT + ")");
            s.executeUpdate("insert into session (id, project_id, name, need, language, origin, state, created_at, updated_at)"
                    + " values ('s1', 'p1', 'Halo', 'Besoin', 'FR', 'LOCAL', 'QUESTIONING', " + AT + ", " + AT + ")");
        }
    }

    static int count(Statement s, String table) throws SQLException {
        try (ResultSet r = s.executeQuery("select count(*) from " + table)) {
            r.next();
            return r.getInt(1);
        }
    }

    @Test
    void leLabelDuProjetEstBorneEtSupprimeAvecLui() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project_jira_label (project_id, label, updated_at) values ('p1', 'spec-writer', " + AT + ")");
            assertThatThrownBy(() -> s.executeUpdate("update project_jira_label set label = ''")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("update project_jira_label set label = '" + "x".repeat(256) + "'"))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate("insert into ticket_label (project_id, ticket_key, label) values ('p1', 'AUR-1', 'spec-writer')");
            assertThatThrownBy(() -> s.executeUpdate(
                    "insert into ticket_label (project_id, ticket_key, label) values ('p1', 'AUR-1', 'spec-writer')"))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate("delete from session where id = 's1'");
            s.executeUpdate("delete from project where id = 'p1'");
            assertThat(count(s, "project_jira_label")).isZero();
            assertThat(count(s, "ticket_label")).isZero();
        }
    }

    @Test
    void leLienPerduSuitLaSession() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into lost_jira_link (session_id, issue_key, lost_at) values ('s1', 'AUR-12', " + AT + ")");
            s.executeUpdate("delete from session where id = 's1'");
            assertThat(count(s, "lost_jira_link")).isZero();
        }
    }
}
