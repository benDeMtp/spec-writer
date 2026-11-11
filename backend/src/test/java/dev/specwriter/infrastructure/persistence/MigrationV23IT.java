package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Migration V23 (feature 012, T030, FR-016) : la clé Jira par défaut devient facultative ; les
 * réglages Jira existants sont conservés tels quels.
 */
class MigrationV23IT {

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void baseV22() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        flyway("22").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into jira_settings (id, enabled, server_url, default_project_key, ca_certificate_path,"
                    + " server_version, last_test_at, last_test_result, secret_persistence, insecure_http) values"
                    + " (1, 1, 'https://jira.example.com', 'PROJ', '/etc/ca.pem', '9.12.3', '2026-11-01T09:00:00Z', 'OK',"
                    + " 'KEYCHAIN', 0)");
        }
    }

    Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void lesReglagesExistantsSontConservesEtLaCleDevientFacultative() throws SQLException {
        flyway("23").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("select enabled, server_url, default_project_key, ca_certificate_path,"
                    + " server_version, last_test_at, last_test_result, secret_persistence, insecure_http"
                    + " from jira_settings")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getInt(1)).isEqualTo(1);
                assertThat(r.getString(2)).isEqualTo("https://jira.example.com");
                assertThat(r.getString(3)).isEqualTo("PROJ");
                assertThat(r.getString(4)).isEqualTo("/etc/ca.pem");
                assertThat(r.getString(5)).isEqualTo("9.12.3");
                assertThat(r.getString(6)).isEqualTo("2026-11-01T09:00:00Z");
                assertThat(r.getString(7)).isEqualTo("OK");
                assertThat(r.getString(8)).isEqualTo("KEYCHAIN");
                assertThat(r.getInt(9)).isZero();
                assertThat(r.next()).isFalse();
            }
            s.executeUpdate("update jira_settings set default_project_key = null where id = 1");
            try (ResultSet r = s.executeQuery("select default_project_key from jira_settings")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isNull();
            }
        }
    }
}
