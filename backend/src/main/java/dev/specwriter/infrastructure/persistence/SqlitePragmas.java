package dev.specwriter.infrastructure.persistence;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Durcit les fichiers SQLite après les migrations (FR-022) : base, journal WAL et mémoire partagée
 * en {@code rw-------} sous POSIX. Les pragmas WAL, foreign_keys et busy_timeout sont posés à
 * chaque connexion par les paramètres de l'URL JDBC (DataDirConfigSource, SC-006).
 */
@ApplicationScoped
public class SqlitePragmas {

    @ConfigProperty(name = "specwriter.data-dir.resolved")
    String dataDir;

    void onStart(@Observes StartupEvent event) {
        try {
            if (!Files.getFileStore(dataDirPath()).supportsFileAttributeView("posix")) {
                return;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier de données inaccessible", e);
        }
        for (String name : new String[] {"spec-writer.db", "spec-writer.db-wal", "spec-writer.db-shm"}) {
            Path file = dataDirPath().resolve(name);
            if (Files.exists(file)) {
                try {
                    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
                } catch (IOException e) {
                    throw new UncheckedIOException("Impossible de protéger " + name, e);
                }
            }
        }
    }

    private Path dataDirPath() {
        return Path.of(dataDir);
    }
}
