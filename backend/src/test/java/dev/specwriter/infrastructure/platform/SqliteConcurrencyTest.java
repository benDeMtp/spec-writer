package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Une transaction qui lit puis écrit (création d'une session) ne doit pas échouer en
 * {@code SQLITE_BUSY} parce que l'indexation a écrit entre-temps (FR-033 de 002, vu en E2E, T117) :
 * chaque transaction prend le verrou d'écriture dès son début et attend son tour (busy_timeout).
 */
class SqliteConcurrencyTest {

    @TempDir
    Path dir;

    @Test
    void unLecteurQuiEcritAttendSonTourPendantUneIndexation() throws Exception {
        String url = DataDirConfigSource.jdbcUrl(dir.resolve("spec-writer.db"));
        assertThat(url).contains("journal_mode=WAL", "busy_timeout=5000", "transaction_mode=IMMEDIATE");
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("create table item (id integer primary key, v text)");
        }
        CountDownLatch read = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> session = threads.submit(() -> {
                try (Connection c = DriverManager.getConnection(url)) {
                    c.setAutoCommit(false);
                    try (Statement s = c.createStatement()) {
                        s.executeQuery("select count(*) from item").close();
                        read.countDown();
                        Thread.sleep(300);
                        s.executeUpdate("insert into item (v) values ('session')");
                    }
                    c.commit();
                }
                return null;
            });
            Future<?> indexation = threads.submit(() -> {
                read.await(5, TimeUnit.SECONDS);
                try (Connection c = DriverManager.getConnection(url)) {
                    c.setAutoCommit(false);
                    try (Statement s = c.createStatement()) {
                        s.executeUpdate("insert into item (v) values ('indexation')");
                    }
                    c.commit();
                }
                return null;
            });
            session.get(10, TimeUnit.SECONDS);
            indexation.get(10, TimeUnit.SECONDS);
        } finally {
            threads.shutdownNow();
        }
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement();
                var r = s.executeQuery("select count(*) from item")) {
            r.next();
            assertThat(r.getInt(1)).isEqualTo(2);
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }
}
