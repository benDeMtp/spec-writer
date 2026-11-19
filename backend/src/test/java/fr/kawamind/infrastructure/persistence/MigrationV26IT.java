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
 * Migration V26 (feature 015, T001) : tâches d'une spécification, préalables, proposition en cours,
 * suivi de la session et journal des agents étendu ; les lignes du journal sont conservées.
 */
class MigrationV26IT {

    private static final String AT = "'2026-11-17T09:00:00Z'";

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void baseV25() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        flyway("25").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at)"
                    + " values ('p1', 'Aurora', 'aurora', " + AT + ", " + AT + ")");
            s.executeUpdate("insert into session (id, project_id, name, need, language, origin, state, created_at, updated_at)"
                    + " values ('s1', 'p1', 'Rétractation', 'Besoin', 'FR', 'LOCAL', 'QUESTIONING', " + AT + ", " + AT + ")");
            s.executeUpdate("insert into agent_access_log (id, at, agent_name, operation, project_id, session_id,"
                    + " old_status, new_status) values ('l1', " + AT + ", 'claude-code', 'SET_STATUS', 'p1', 's1',"
                    + " 'READY', 'IN_PROGRESS')");
        }
        flyway("26").migrate();
    }

    Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void leJournalEstConserveEtAccepteLesOperationsDeTaches() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("select operation, old_status, new_status, task_key from agent_access_log")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("SET_STATUS");
                assertThat(r.getString(2)).isEqualTo("READY");
                assertThat(r.getString(3)).isEqualTo("IN_PROGRESS");
                assertThat(r.getString(4)).isNull();
            }
            for (String op : new String[] {"NEXT_TASK", "START_TASK", "COMPLETE_TASK", "RELEASE_TASK"}) {
                s.executeUpdate(log("l-" + op, op));
            }
            assertThatThrownBy(() -> s.executeUpdate(log("l9", "DELETE_TASK"))).isInstanceOf(SQLException.class);
            try (ResultSet r = s.executeQuery("select count(*) from sqlite_master where name = 'agent_access_log_at'")) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void leSuiviEstActifParDefaut() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("select task_tracking from session where id = 's1'")) {
                r.next();
                assertThat(r.getString(1)).isEqualTo("ACTIVE");
            }
            assertThatThrownBy(() -> s.executeUpdate("update session set task_tracking = 'PAUSE'"))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate("update session set task_tracking = 'SUSPENDED'");
        }
    }

    @Test
    void uneTacheVerifieSesValeurs() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate(task("t1", 0, "'Exposer l''endpoint'", "'POST renvoie 201'", "'PROPOSED'", "'CREATED'",
                    "'FACT-124'", "'TODO'"));
            assertThatThrownBy(() -> s.executeUpdate(task("t2", 0, "'Autre'", "'Résultat'", "'PROPOSED'",
                    "'NOT_CREATED'", "null", "'TODO'"))).as("position unique").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(task("t3", 1, "'" + "x".repeat(121) + "'", "'R'", "'PROPOSED'",
                    "'NOT_CREATED'", "null", "'TODO'"))).as("titre > 120").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(task("t4", 1, "'T'", "'" + "x".repeat(301) + "'", "'PROPOSED'",
                    "'NOT_CREATED'", "null", "'TODO'"))).as("résultat > 300").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(task("t5", 1, "'T'", "''", "'PROPOSED'", "'NOT_CREATED'", "null",
                    "'TODO'"))).as("résultat vide hors JIRA").isInstanceOf(SQLException.class);
            s.executeUpdate(task("t6", 1, "'Sous-tâche Jira'", "''", "'JIRA'", "'CREATED'", "'FACT-125'", "'DONE'"));
            assertThatThrownBy(() -> s.executeUpdate(task("t7", 2, "'T'", "'R'", "'PROPOSED'", "'CREATED'", "null",
                    "'TODO'"))).as("clé requise si créée").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(task("t8", 2, "'T'", "'R'", "'PROPOSED'", "'CREATED'",
                    "'FACT-124'", "'TODO'"))).as("clé unique").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(task("t9", 2, "'T'", "'R'", "'PROPOSED'", "'NOT_CREATED'", "null",
                    "'BLOCKED'"))).as("statut").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("update spec_task set completion_report = '" + "x".repeat(2001)
                    + "' where id = 't1'")).as("compte rendu > 2000").isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("update spec_task set pending_target = 'MISSING' where id = 't1'"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void lesTachesLeursPrealablesEtLaPropositionSontSupprimesAvecLaSession() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate(task("t1", 0, "'A'", "'R'", "'PROPOSED'", "'NOT_CREATED'", "null", "'TODO'"));
            s.executeUpdate(task("t2", 1, "'B'", "'R'", "'PROPOSED'", "'NOT_CREATED'", "null", "'TODO'"));
            s.executeUpdate("insert into spec_task_dependency (task_id, depends_on_id) values ('t2', 't1')");
            s.executeUpdate("insert into spec_task_proposal (session_id, content, created_at) values ('s1', '[]', "
                    + AT + ")");
            s.executeUpdate("delete from session where id = 's1'");
            assertThat(count(s, "spec_task")).isZero();
            assertThat(count(s, "spec_task_dependency")).isZero();
            assertThat(count(s, "spec_task_proposal")).isZero();
        }
    }

    private static String log(String id, String operation) {
        return "insert into agent_access_log (id, at, agent_name, operation, project_id, session_id, old_status,"
                + " new_status, task_key) values ('" + id + "', " + AT + ", 'claude-code', '" + operation
                + "', 'p1', 's1', null, null, 'FACT-124')";
    }

    private static String task(String id, int position, String title, String outcome, String origin, String creation,
            String key, String status) {
        return "insert into spec_task (id, session_id, position, title, outcome, basis_kind, basis_ref, origin, creation,"
                + " jira_key, status) values ('" + id + "', 's1', " + position + ", " + title + ", " + outcome
                + ", 'USE_CASE', 'UC-01', " + origin + ", " + creation + ", " + key + ", " + status + ")";
    }

    private static int count(Statement s, String table) throws SQLException {
        try (ResultSet r = s.executeQuery("select count(*) from " + table)) {
            r.next();
            return r.getInt(1);
        }
    }
}
