package fr.kawamind.infrastructure.platform;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Verrou exclusif de l'instance sur {@code instance.lock}, dans le dossier de données (019,
 * research.md R1). Tenu toute la vie du processus ; le système le rend à la mort du processus,
 * même après un arrêt brutal : il n'existe donc pas de verrou abandonné.
 */
public final class InstanceLock {

    public static final String FILE = "instance.lock";

    public enum Outcome { ACQUIRED, BUSY }

    private static volatile InstanceLock held;

    private final Path dataDir;
    private final FileChannel channel;
    private final FileLock lock;

    private InstanceLock(Path dataDir, FileChannel channel, FileLock lock) {
        this.dataDir = dataDir;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Prend le verrou du dossier de données.
     *
     * @throws IOException si le dossier ne permet pas de créer ou d'ouvrir le fichier : ce n'est pas
     *     une instance en marche
     */
    public static synchronized Outcome tryAcquire(Path dataDir) throws IOException {
        if (held != null && held.dataDir.equals(dataDir)) {
            return Outcome.ACQUIRED;
        }
        FileChannel channel = FileChannel.open(dataDir.resolve(FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        }
        if (lock == null) {
            channel.close();
            return Outcome.BUSY;
        }
        held = new InstanceLock(dataDir, channel, lock);
        return Outcome.ACQUIRED;
    }

    /** Dossier de données dont ce processus tient le verrou, s'il en tient un. */
    public static Optional<Path> heldDataDir() {
        InstanceLock lock = held;
        return lock == null ? Optional.empty() : Optional.of(lock.dataDir);
    }

    public static boolean held() {
        return held != null;
    }

    /** Rend le verrou ; utile aux tests, le processus le rend de lui-même à sa fin. */
    static synchronized void release() throws IOException {
        InstanceLock lock = held;
        held = null;
        if (lock != null) {
            lock.lock.release();
            lock.channel.close();
        }
    }
}
