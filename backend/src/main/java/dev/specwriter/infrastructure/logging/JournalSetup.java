package dev.specwriter.infrastructure.logging;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Branche le journal des erreurs sur le logger racine au démarrage, et le retire à l'arrêt (research.md R15). */
@ApplicationScoped
public class JournalSetup {

    @ConfigProperty(name = "specwriter.data-dir.resolved")
    String dataDir;

    private JournalHandler handler;

    Path dir() {
        return Path.of(dataDir, "logs");
    }

    void start(@Observes StartupEvent event) {
        try {
            Files.createDirectories(dir());
            handler = new JournalHandler(dir());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Logger.getLogger("").addHandler(handler);
    }

    void stop(@Observes ShutdownEvent event) {
        if (handler != null) {
            Logger.getLogger("").removeHandler(handler);
            handler.close();
            handler = null;
        }
    }

    @Produces
    @ApplicationScoped
    LogJournal logJournal() {
        return new LogJournal(dir());
    }
}
