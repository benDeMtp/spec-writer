package dev.specwriter.spike;

import static org.assertj.core.api.Assertions.assertThat;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Spike S2 : SQLite (xerial) + dialecte Hibernate community + Flyway + FTS5 + WAL sous Quarkus 4.
 * Lancer avec : ./mvnw -pl backend verify -Pspike -DskipDependencyCheck
 */
@Tag("spike")
@QuarkusTest
@TestProfile(SqliteHibernateSpikeIT.SpikeProfile.class)
class SqliteHibernateSpikeIT {

    public static class SpikeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Path db;
            try {
                db = Files.createTempDirectory("spike-sqlite").resolve("spike.db");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return Map.of(
                    "quarkus.datasource.db-kind", "other",
                    "quarkus.datasource.jdbc.driver", "org.sqlite.JDBC",
                    "quarkus.datasource.jdbc.url", "jdbc:sqlite:" + db,
                    "quarkus.hibernate-orm.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                    "quarkus.hibernate-orm.schema-management.strategy", "none",
                    "quarkus.flyway.migrate-at-start", "true",
                    "quarkus.flyway.locations", "db/spike");
        }
    }

    @Inject
    EntityManager em;

    @Inject
    AgroalDataSource dataSource;

    @Test
    @Transactional
    void crudAvecLeDialecteCommunity() {
        SpikeNote note = new SpikeNote();
        note.id = UUID.randomUUID().toString();
        note.title = "Règle de réservation";
        note.body = "Une salle ne peut être réservée que par un employé.";
        em.persist(note);
        em.flush();
        em.clear();

        SpikeNote lu = em.find(SpikeNote.class, note.id);
        assertThat(lu.title).isEqualTo("Règle de réservation");

        List<SpikeNote> tous = em.createQuery("from SpikeNote where title like :t", SpikeNote.class)
                .setParameter("t", "Règle%")
                .getResultList();
        assertThat(tous).hasSize(1);
    }

    @Test
    void fts5SansDiacritiquesEtWal() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (var st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                    rs.next();
                    assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
                }
                st.execute("INSERT INTO spike_search(note_id, title, body) VALUES ('1', 'Règle métier', 'Le paiement est refusé')");
            }
            try (var ps = c.prepareStatement("SELECT note_id FROM spike_search WHERE spike_search MATCH ?")) {
                ps.setString(1, "regle");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("« regle » doit trouver « Règle »").isTrue();
                    assertThat(rs.getString(1)).isEqualTo("1");
                }
                ps.setString(1, "refuse");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("« refuse » doit trouver « refusé »").isTrue();
                }
            }
        }
    }
}
