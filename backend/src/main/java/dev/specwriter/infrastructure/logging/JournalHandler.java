package dev.specwriter.infrastructure.logging;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import org.jboss.logmanager.handlers.SizeRotatingFileHandler;

/**
 * Fichier du journal des erreurs : WARN et au-delà, {@code logs/journal.jsonl} dans le dossier de
 * données, 2 Mo par fichier et 4 sauvegardes (FR-031, research.md R15).
 */
public final class JournalHandler extends SizeRotatingFileHandler {

    public static final String FILE = "journal.jsonl";
    public static final long ROTATE_SIZE = 2L * 1024 * 1024;
    public static final int BACKUPS = 4;

    public JournalHandler(Path dir) throws IOException {
        this(dir, ROTATE_SIZE);
    }

    JournalHandler(Path dir, long rotateSize) throws IOException {
        super(dir.resolve(FILE).toFile(), true, rotateSize, BACKUPS);
        setLevel(Level.WARNING);
        setFormatter(new JournalFormatter());
        setAutoFlush(true);
        setEncoding("UTF-8");
    }
}
