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
 * T003 (016) : V27 ajoute les réglages Confluence, les pages lues, les niveaux de fiabilité, la
 * sorte CONFLUENCE des sources de documentation, sans rien perdre des sources existantes ni de
 * l'état des fichiers de leurs dossiers (clés étrangères actives, comme en production).
 */
class MigrationV27IT {

    static final String PROJECT = "00000000-0000-0000-0000-0000000000aa";

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void avantV27() throws SQLException {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db") + "?foreign_keys=true";
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").target("26").load().migrate();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("insert into project (id, name, name_key, created_at, updated_at) values ('" + PROJECT
                    + "', 'Facturation', 'facturation', '2026-10-01T08:00:00Z', '2026-10-01T08:00:00Z')");
            s.executeUpdate("insert into project_doc_source (id, project_id, kind, location, last_read_at, last_result,"
                    + " exclusions) values ('s-antora', '" + PROJECT + "', 'URL', 'https://doc/antora', "
                    + "'2026-10-02T08:00:00Z', '{\"pagesRead\":3}', null)");
            s.executeUpdate("insert into project_doc_source (id, project_id, kind, location, exclusions) values "
                    + "('s-docs', '" + PROJECT + "', 'DOCUMENTS', '/Users/moi/SharePoint', '[\"*.tmp\"]')");
            s.executeUpdate("insert into document_file (source_id, path, size, modified_at) values "
                    + "('s-docs', 'Demande.docx', 1200, '2026-09-12T08:00:00Z')");
        }
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").load().migrate();
    }

    static String single(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    @Test
    void sourcesEtFichiersConserves() throws SQLException {
        try (Connection c = DriverManager.getConnection(url)) {
            assertThat(single(c, "select count(*) from project_doc_source")).isEqualTo("2");
            assertThat(single(c, "select last_result from project_doc_source where id = 's-antora'"))
                    .isEqualTo("{\"pagesRead\":3}");
            assertThat(single(c, "select exclusions from project_doc_source where id = 's-docs'"))
                    .isEqualTo("[\"*.tmp\"]");
            assertThat(single(c, "select modified_at from document_file where source_id = 's-docs'"))
                    .isEqualTo("2026-09-12T08:00:00Z");
            assertThat(single(c, "select count(*) from sqlite_master where name = 'document_file_v27'")).isEqualTo("0");
        }
    }

    @Test
    void racineConfluenceEtSesContraintes() throws SQLException {
        try (Connection c = DriverManager.getConnection(url)) {
            exec(c, "insert into project_doc_source (id, project_id, kind, location, reliability, page_id, title) values"
                    + " ('s-conf', '" + PROJECT + "', 'CONFLUENCE', 'https://wiki/pages/viewpage.action?pageId=100',"
                    + " 'TO_CONFIRM', '100', 'Remboursements')");
            assertThat(single(c, "select title from project_doc_source where id = 's-conf'")).isEqualTo("Remboursements");
            assertThatThrownBy(() -> exec(c, "update project_doc_source set reliability = 'REFERENCE' where id = 's-docs'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> exec(c, "update project_doc_source set page_id = '1' where id = 's-antora'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> exec(c, "update project_doc_source set reliability = 'HAUTE' where id = 's-conf'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> exec(c, "update project_doc_source set title = '" + "x".repeat(501)
                    + "' where id = 's-conf'")).isInstanceOf(SQLException.class);

            exec(c, "insert into confluence_page (source_id, page_id, version, updated_at) values"
                    + " ('s-conf', '105', 2, '2026-09-10T08:00:00Z')");
            assertThatThrownBy(() -> exec(c, "insert into confluence_page (source_id, page_id, version) values"
                    + " ('s-conf', '106', 0)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> exec(c, "insert into confluence_page (source_id, page_id, version) values"
                    + " ('s-conf', '105', 3)")).isInstanceOf(SQLException.class);
            exec(c, "delete from project_doc_source where id = 's-conf'");
            assertThat(single(c, "select count(*) from confluence_page")).isEqualTo("0");
        }
    }

    @Test
    void reglagesConfluenceEtNiveaux() throws SQLException {
        try (Connection c = DriverManager.getConnection(url)) {
            exec(c, "insert into confluence_settings (id, server_url, secret_persistence) values (1, 'https://wiki', 'KEYCHAIN')");
            assertThatThrownBy(() -> exec(c, "insert into confluence_settings (id, server_url, secret_persistence)"
                    + " values (2, 'https://autre', 'KEYCHAIN')")).isInstanceOf(SQLException.class);

            exec(c, "insert into project_source_reliability (project_id, origin, level) values ('" + PROJECT
                    + "', 'CONFLUENCE', 'TO_CONFIRM')");
            assertThatThrownBy(() -> exec(c, "insert into project_source_reliability (project_id, origin, level) values ('"
                    + PROJECT + "', 'SHEET', 'REFERENCE')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> exec(c, "insert into project_source_reliability (project_id, origin, level) values ('"
                    + PROJECT + "', 'JIRA', 'HAUTE')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> exec(c, "insert into project_source_reliability (project_id, origin, level) values ('"
                    + PROJECT + "', 'CONFLUENCE', 'REFERENCE')")).isInstanceOf(SQLException.class);
            assertThat(single(c, "select count(*) from pragma_table_info('conversation_source') where name in"
                    + " ('origin', 'updated_at', 'level')")).isEqualTo("3");
        }
    }
}
