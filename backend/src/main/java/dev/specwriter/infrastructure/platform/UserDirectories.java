package dev.specwriter.infrastructure.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dossiers utilisateur standard de chaque OS (research.md R9). Sous Windows, {@code %LOCALAPPDATA%}
 * est préféré au profil itinérant pour ne pas répliquer de données confidentielles. La propriété
 * {@code specwriter.data-dir} est prioritaire (tests, E2E).
 */
public final class UserDirectories {

    public static final String APP = "spec-writer";
    public static final String DATA_DIR_PROPERTY = "specwriter.data-dir";

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    private final Map<String, String> env;
    private final Map<String, String> props;

    public UserDirectories(Map<String, String> env, Map<String, String> props) {
        this.env = Map.copyOf(env);
        this.props = Map.copyOf(props);
    }

    /** Dossiers résolus à partir de l'environnement réel du processus. */
    public static UserDirectories fromSystem() {
        Map<String, String> props = new java.util.HashMap<>();
        for (String key : new String[] {"os.name", "user.home", DATA_DIR_PROPERTY}) {
            String value = System.getProperty(key);
            if (value != null) {
                props.put(key, value);
            }
        }
        return new UserDirectories(System.getenv(), props);
    }

    public Path dataDir() {
        String override = props.get(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return switch (os()) {
            case MAC -> macBase();
            case WINDOWS -> windowsBase().resolve("data");
            case LINUX -> xdg("XDG_DATA_HOME", Path.of(home(), ".local", "share")).resolve(APP);
        };
    }

    public Path configDir() {
        String override = props.get(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override, "config");
        }
        return switch (os()) {
            case MAC -> macBase().resolve("config");
            case WINDOWS -> windowsBase().resolve("config");
            case LINUX -> xdg("XDG_CONFIG_HOME", Path.of(home(), ".config")).resolve(APP);
        };
    }

    public boolean posix() {
        return os() != Os.WINDOWS;
    }

    /** Crée le dossier (et ses parents) ; le dossier final est en {@code rwx------} sous POSIX. */
    public static Path createPrivateDirectories(Path dir, boolean posix) {
        try {
            Files.createDirectories(dir);
            if (posix) {
                Files.setPosixFilePermissions(dir, OWNER_ONLY);
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer le dossier de données", e);
        }
    }

    private Path macBase() {
        return Path.of(home(), "Library", "Application Support", APP);
    }

    private Path windowsBase() {
        String local = env.get("LOCALAPPDATA");
        Path base = local != null && !local.isBlank() ? Path.of(local) : Path.of(home(), "AppData", "Local");
        return base.resolve(APP);
    }

    private Path xdg(String variable, Path fallback) {
        String value = env.get(variable);
        // La spécification XDG impose un chemin absolu ; une valeur relative est ignorée.
        if (value != null && value.startsWith("/")) {
            return Path.of(value);
        }
        return fallback;
    }

    private String home() {
        String home = props.get("user.home");
        if (home == null) {
            throw new IllegalStateException("user.home non défini");
        }
        return home;
    }

    private Os os() {
        String name = props.getOrDefault("os.name", "").toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return Os.WINDOWS;
        }
        if (name.startsWith("mac") || name.startsWith("darwin")) {
            return Os.MAC;
        }
        return Os.LINUX;
    }

    private enum Os {
        MAC,
        WINDOWS,
        LINUX
    }
}
