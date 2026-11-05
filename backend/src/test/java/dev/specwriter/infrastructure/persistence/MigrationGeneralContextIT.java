package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T112 : une base V9 avec deux fiches et des réponses Q-n migre vers une fiche générale
 * (research.md R25) ; les colonnes de la fiche technique, l'origine et la table des questions
 * disparaissent ; rien d'autre ne change.
 */
class MigrationGeneralContextIT {

    static final String BOTH = "11111111-1111-1111-1111-111111111111";
    static final String ONLY = "22222222-2222-2222-2222-222222222222";
    static final String LONG = "33333333-3333-3333-3333-333333333333";
    static final String DEFAULT = "00000000-0000-0000-0000-000000000001";

    @TempDir
    Path dir;
    String url;
    final List<LogRecord> warnings = new ArrayList<>();
    final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord r) {
            warnings.add(r);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void setUp() {
        url = "jdbc:sqlite:" + dir.resolve("spec-writer.db");
        Logger.getLogger("db.migration.V11__general_context").addHandler(capture);
    }

    @AfterEach
    void tearDown() {
        Logger.getLogger("db.migration.V11__general_context").removeHandler(capture);
    }

    Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }

    static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    void project(Connection c, String id, String name, String functional, String fAt, String technical, String tAt)
            throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                insert into project (id, name, name_key, jira_project_key, created_at, updated_at, context, context_hash,
                  context_origin, context_updated_at, technical_context, technical_context_hash,
                  technical_context_origin, technical_context_updated_at)
                values (?, ?, ?, 'FACT', '2026-10-01T08:00:00Z', '2026-10-02T08:00:00Z', ?, 'ancien', 'GENERATED', ?, ?,
                  'ancien-t', 'EDITED', ?)""")) {
            p.setString(1, id);
            p.setString(2, name);
            p.setString(3, name.toLowerCase());
            p.setString(4, functional);
            p.setString(5, fAt);
            p.setString(6, technical);
            p.setString(7, tAt);
            p.executeUpdate();
        }
    }

    void question(Connection c, String project, int number, String sheet, String label, String answer, String state)
            throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                insert into project_context_question (id, project_id, number, sheet, label, rationale, answer, state,
                  asked_at, answered_at) values (?, ?, ?, ?, ?, '', ?, ?, '2026-10-03T08:00:00Z', '2026-10-03T08:05:00Z')""")) {
            p.setString(1, project + "-" + number);
            p.setString(2, project);
            p.setInt(3, number);
            p.setString(4, sheet);
            p.setString(5, label);
            p.setString(6, answer);
            p.setString(7, state);
            p.executeUpdate();
        }
    }

    Map<String, String> row(Connection c, String id) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select * from project where id = '" + id + "'")) {
            assertThat(r.next()).isTrue();
            Map<String, String> out = new LinkedHashMap<>();
            for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
                out.put(r.getMetaData().getColumnName(i), r.getString(i));
            }
            return out;
        }
    }

    static List<String> columns(Connection c, String table) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("pragma table_info(" + table + ")")) {
            while (r.next()) {
                out.add(r.getString("name"));
            }
        }
        return out;
    }

    @Test
    void deuxFichesEtReponsesFusionneesEnUneFicheGenerale() throws Exception {
        flyway("9").migrate();
        try (Connection c = DriverManager.getConnection(url)) {
            project(c, BOTH, "Facturation", "## Acteurs\n- Le gestionnaire émet les avoirs.", "2026-10-05T08:00:00Z",
                    "## Architecture\n- Les services passent par Kafka.", "2026-10-06T09:00:00Z");
            question(c, BOTH, 2, "TECHNICAL", "Quelle base ?", "PostgreSQL", "ANSWERED");
            question(c, BOTH, 1, "FUNCTIONAL", "Qui valide ?", "Le responsable", "ANSWERED");
            question(c, BOTH, 3, "FUNCTIONAL", "Quel délai ?", "", "SKIPPED");
            project(c, ONLY, "Congés", "## Acteurs\n- Le salarié pose ses congés.", "2026-10-05T08:00:00Z", "", null);
            project(c, LONG, "Long", "a".repeat(15_000), "2026-10-05T08:00:00Z", "b".repeat(15_000),
                    "2026-10-04T08:00:00Z");
        }
        Map<String, String> before;
        try (Connection c = DriverManager.getConnection(url)) {
            before = row(c, ONLY);
        }

        flyway(null).migrate();

        try (Connection c = DriverManager.getConnection(url)) {
            assertThat(columns(c, "project")).doesNotContain("technical_context", "technical_context_hash",
                    "technical_context_origin", "technical_context_updated_at", "context_origin")
                    .contains("context", "context_hash", "context_updated_at", "architecture_workspace");
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                    "select count(*) from sqlite_master where name = 'project_context_question'")) {
                r.next();
                assertThat(r.getInt(1)).isZero();
            }

            Map<String, String> both = row(c, BOTH);
            String expected = """
                    ## Fonctionnel

                    ## Acteurs
                    - Le gestionnaire émet les avoirs.

                    ## Technique

                    ## Architecture
                    - Les services passent par Kafka.

                    ## Réponses de clarification

                    - Q-1 Qui valide ? Le responsable
                    - Q-2 Quelle base ? PostgreSQL""";
            assertThat(both.get("context")).isEqualTo(expected);
            assertThat(both.get("context_hash")).isEqualTo(sha256(expected));
            assertThat(both.get("context_updated_at")).isEqualTo("2026-10-06T09:00:00Z");
            // rien d'autre ne change
            assertThat(both.get("name")).isEqualTo("Facturation");
            assertThat(both.get("jira_project_key")).isEqualTo("FACT");
            assertThat(both.get("updated_at")).isEqualTo("2026-10-02T08:00:00Z");

            // une seule fiche, sans réponse : inchangée
            Map<String, String> only = row(c, ONLY);
            assertThat(only.get("context")).isEqualTo(before.get("context"));
            assertThat(only.get("context_hash")).isEqualTo(sha256(before.get("context")));
            assertThat(only.get("context_updated_at")).isEqualTo("2026-10-05T08:00:00Z");

            // tronquée à 20 000 caractères, avec un avertissement
            Map<String, String> tooLong = row(c, LONG);
            assertThat(tooLong.get("context")).hasSize(20_000).startsWith("## Fonctionnel\n\naaa");
            assertThat(warnings).singleElement().satisfies(w -> assertThat(w.getMessage() + " "
                    + java.util.Arrays.toString(w.getParameters())).contains(LONG).contains("20 000"));

            // le projet par défaut, sans fiche, reste vide
            Map<String, String> none = row(c, DEFAULT);
            assertThat(none.get("context")).isEmpty();
            assertThat(none.get("context_hash")).isEmpty();
        }
        assertThat(Files.exists(dir.resolve("spec-writer.db"))).isTrue();
    }
}
