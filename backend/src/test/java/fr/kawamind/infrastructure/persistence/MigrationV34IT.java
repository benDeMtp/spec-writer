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

/**
 * Migration V34 (022, research R4) : le type de la sous-tâche relu dans Jira, nul par défaut et
 * borné à 100 caractères.
 */
class MigrationV34IT {

    private static final String AT = "'2026-11-25T09:00:00Z'";

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void baseV34() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target("34").load()
                .migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at)"
                    + " values ('p1', 'Aurora', 'aurora', " + AT + ", " + AT + ")");
            s.executeUpdate("insert into session (id, project_id, name, need, language, origin, state, created_at, updated_at)"
                    + " values ('s1', 'p1', 'Halo', 'Besoin', 'FR', 'LOCAL', 'QUESTIONING', " + AT + ", " + AT + ")");
            s.executeUpdate("insert into spec_task (id, session_id, position, title, outcome, basis_kind, origin, creation,"
                    + " jira_key, status) values ('t1', 's1', 0, 'Accès', 'Installer', 'NONE', 'PROPOSED', 'CREATED',"
                    + " 'AURORA-182', 'TODO')");
        }
    }

    @Test
    void leTypeEstNulParDefautEtBorne() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("select jira_issue_type from spec_task where id = 't1'")) {
                r.next();
                assertThat(r.getString(1)).isNull();
            }
            s.executeUpdate("update spec_task set jira_issue_type = 'Sous-Tâche (AGILE)' where id = 't1'");
            assertThatThrownBy(() -> s.executeUpdate(
                    "update spec_task set jira_issue_type = '" + "x".repeat(101) + "' where id = 't1'"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
