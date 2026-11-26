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

/** Migration V35 (023, research R1) : réglage du suivi des tâches d'un projet, supprimé avec lui. */
class MigrationV35IT {

    private static final String AT = "'2026-11-26T09:00:00Z'";

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void baseV35() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target("35").load()
                .migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at)"
                    + " values ('p1', 'Aurora', 'aurora', " + AT + ", " + AT + ")");
        }
    }

    @Test
    void unReglageParProjetBorneEtSupprimeAvecLui() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project_task_workflow (project_id, arrival_status, finished_statuses, updated_at)"
                    + " values ('p1', 'Réalisée', '[\"Réalisée\"]', " + AT + ")");
            assertThatThrownBy(() -> s.executeUpdate("update project_task_workflow set arrival_status = ''"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(
                    "update project_task_workflow set arrival_status = '" + "x".repeat(101) + "'"))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate("delete from project where id = 'p1'");
            try (ResultSet r = s.executeQuery("select count(*) from project_task_workflow")) {
                r.next();
                assertThat(r.getInt(1)).isZero();
            }
        }
    }
}
