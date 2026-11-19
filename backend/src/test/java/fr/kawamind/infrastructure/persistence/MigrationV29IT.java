package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T010 (018, research.md R1) : V29 laisse l'assistant « à faire » sur une base neuve et le marque
 * terminé sur une installation déjà utilisée.
 */
class MigrationV29IT {

    @TempDir
    Path dir;

    String migrate(String... before) throws SQLException {
        String url = "jdbc:sqlite:" + dir.resolve("spec-writer-" + System.nanoTime() + ".db") + "?foreign_keys=true";
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target("28").load().migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            for (String sql : before) {
                s.executeUpdate(sql);
            }
        }
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").load().migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement();
                ResultSet r = s.executeQuery("select current_step || '|' || (completed_at is not null) from onboarding")) {
            return r.next() ? r.getString(1) : null;
        }
    }

    @Test
    void baseNeuveAFaire() throws SQLException {
        assertThat(migrate()).isEqualTo("WELCOME|0");
    }

    @Test
    void uneSourceDeDocumentationSuffit() throws SQLException {
        assertThat(migrate("insert into project_doc_source (id, project_id, kind, location) values ('s1', "
                + "'00000000-0000-0000-0000-000000000001', 'URL', 'https://docs.example.com/')")).isEqualTo("DONE|1");
    }

    @Test
    void copilotConnecteSuffit() throws SQLException {
        assertThat(migrate("insert into copilot_settings (id, connected_login, subscription_state, auth_mode, "
                + "secret_persistence) values (1, 'mona', 'ACTIVE', 'COPILOT_CLI', 'KEYCHAIN')")).isEqualTo("DONE|1");
    }

    @Test
    void jiraConfigureSuffit() throws SQLException {
        assertThat(migrate("insert into jira_settings (id, enabled, server_url, default_project_key, "
                + "secret_persistence, insecure_http) values (1, 1, 'https://jira', null, 'KEYCHAIN', 0)"))
                .isEqualTo("DONE|1");
    }

    @Test
    void unSecondProjetSuffit() throws SQLException {
        assertThat(migrate("insert into project (id, name, name_key, created_at, updated_at) values ('p2', 'Aurora', "
                + "'aurora', '2026-10-01T08:00:00Z', '2026-10-01T08:00:00Z')")).isEqualTo("DONE|1");
    }
}
