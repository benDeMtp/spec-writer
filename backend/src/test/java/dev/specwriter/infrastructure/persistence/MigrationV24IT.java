package dev.specwriter.infrastructure.persistence;

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
 * Migration V24 (feature 013, T003) : racines de sources, analyses de périmètre, instantané de la
 * session et indice de module des éléments d'architecture ; les données existantes sont conservées.
 */
class MigrationV24IT {

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void baseV23() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        flyway("23").migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at)"
                    + " values ('p1', 'Aurora', 'aurora', '2026-11-11T09:00:00Z', '2026-11-11T09:00:00Z')");
            s.executeUpdate("insert into conversation (id, project_id, name, created_at, updated_at)"
                    + " values ('c1', 'p1', 'Périmètre', '2026-11-11T09:00:00Z', '2026-11-11T09:00:00Z')");
            s.executeUpdate("insert into conversation_message (id, conversation_id, ordinal, role, text, state, created_at)"
                    + " values ('m1', 'c1', 1, 'AGENT', 'Réponse', 'COMPLETE', '2026-11-11T09:00:00Z')");
            s.executeUpdate("insert into knowledge_item (id, project_id, kind, ref, chunk, title, body, content_hash, indexed_at)"
                    + " values ('k1', 'p1', 'ARCH_ELEMENT', 'arch:Facturation', 0, 'Facturation', 'Facturation', 'h',"
                    + " '2026-11-11T09:00:00Z')");
        }
        flyway("24").migrate();
    }

    Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void lesDonneesExistantesSontConserveesEtLIndiceDeModuleEstFacultatif() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("select title, module_hint from knowledge_item where ref = 'arch:Facturation'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("Facturation");
                assertThat(r.getString(2)).isNull();
            }
            s.executeUpdate("update knowledge_item set module_hint = 'modules/facturation' where ref = 'arch:Facturation'");
        }
    }

    @Test
    void uneRacineEstUniqueParProjetEtSupprimeeAvecLui() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate(root("r1", "/src/rv"));
            assertThatThrownBy(() -> s.executeUpdate(root("r2", "/src/rv"))).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(root("r3", ""))).isInstanceOf(SQLException.class);
            s.executeUpdate("delete from project where id = 'p1'");
            assertThat(count(s, "project_source_root")).isZero();
        }
    }

    @Test
    void uneAnalyseEstSupprimeeAvecSaConversationEtVerifieSesValeurs() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate(analysis("a1", 1, "COMPLETE"));
            assertThatThrownBy(() -> s.executeUpdate(analysis("a2", 6, "COMPLETE"))).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(analysis("a3", 2, "PARTIEL"))).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(analysis("a4", 1, "COMPLETE"))).isInstanceOf(SQLException.class);
            s.executeUpdate(module("pm1", "MAVEN", "UNQUALIFIED", "null"));
            assertThatThrownBy(() -> s.executeUpdate(module("pm2", "GRADLE", "UNQUALIFIED", "null")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(module("pm3", "MAVEN", "PEUT_ETRE", "null")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(module("pm4", "MAVEN", "TO_CHECK", "'" + "x".repeat(301) + "'")))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate(occurrence(1, "'ContractStatus status;'"));
            assertThatThrownBy(() -> s.executeUpdate(occurrence(2, "'" + "x".repeat(201) + "'")))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate("delete from conversation where id = 'c1'");
            assertThat(count(s, "perimeter_analysis")).isZero();
            assertThat(count(s, "perimeter_module")).isZero();
            assertThat(count(s, "perimeter_occurrence")).isZero();
        }
    }

    @Test
    void lInstantaneEstSupprimeAvecSaSession() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into session (id, project_id, name, need, language, origin, state, created_at, updated_at)"
                    + " values ('s1', 'p1', 'Statut suspendu', 'Besoin', 'FR', 'LOCAL', 'QUESTIONING',"
                    + " '2026-11-11T09:00:00Z', '2026-11-11T09:00:00Z')");
            s.executeUpdate("insert into session_perimeter (session_id, snapshot, taken_at)"
                    + " values ('s1', '{}', '2026-11-11T09:00:00Z')");
            s.executeUpdate("delete from session where id = 's1'");
            assertThat(count(s, "session_perimeter")).isZero();
        }
    }

    private static String root(String id, String location) {
        return "insert into project_source_root (id, project_id, location, exclusions, created_at)"
                + " values ('" + id + "', 'p1', '" + location + "', null, '2026-11-11T09:00:00Z')";
    }

    private static String analysis(String id, int ordinal, String state) {
        return "insert into perimeter_analysis (id, message_id, ordinal, terms, rejected_terms, roots, state,"
                + " incomplete_reason, files_scanned, files_skipped, skipped_examples, duration_ms, architecture_only,"
                + " unavailable) values ('" + id + "', 'm1', " + ordinal + ", '[\"ContractStatus\"]', '[]', '[]', '"
                + state + "', null, 10, 0, '[]', 12, '[]', null)";
    }

    private static String module(String id, String kind, String qualification, String justification) {
        return "insert into perimeter_module (id, analysis_id, position, root_id, root_label, path, name, kind,"
                + " occurrences, reasons, qualification, justification, is_new, arch_element, arch_status) values ('" + id
                + "', 'a1', 1, 'r1', 'rv', 'libs/common', 'common', '" + kind + "', 3, '[]', '" + qualification + "', "
                + justification + ", 0, null, null)";
    }

    private static String occurrence(int position, String excerpt) {
        return "insert into perimeter_occurrence (module_id, position, path, line, term, excerpt) values ('pm1', "
                + position + ", 'src/A.java', 12, 'ContractStatus', " + excerpt + ")";
    }

    private static int count(Statement s, String table) throws SQLException {
        try (ResultSet r = s.executeQuery("select count(*) from " + table)) {
            r.next();
            return r.getInt(1);
        }
    }
}
