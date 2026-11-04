package dev.specwriter.infrastructure.platform;

import io.quarkus.runtime.LaunchMode;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Fournit au démarrage les valeurs qui dépendent du poste (research.md R9, R14) :
 * l'URL JDBC de la base dans le dossier de données de l'OS (créé en {@code rwx------}), avec les
 * pragmas SQLite, et le port HTTP effectivement libre.
 */
public class DataDirConfigSource implements ConfigSource {

    /** Inférieur à application.properties (250) : une valeur explicite de profil reste prioritaire. */
    private static final int ORDINAL = 200;

    private final Map<String, String> values;

    public DataDirConfigSource() {
        UserDirectories dirs = UserDirectories.fromSystem();
        Path data = UserDirectories.createPrivateDirectories(dirs.dataDir(), dirs.posix());
        String url = jdbcUrl(data.resolve("spec-writer.db"));
        int port = PortSelector.choose(Integer.getInteger("specwriter.port", PortSelector.DEFAULT_PORT), LaunchMode.current());
        this.values = Map.of(
                "quarkus.datasource.jdbc.url", url,
                "quarkus.http.port", Integer.toString(port),
                "specwriter.data-dir.resolved", data.toString());
    }

    /**
     * URL JDBC de la base : WAL, clés étrangères, attente de 5 s sur un verrou, et verrou d'écriture
     * pris dès le début de chaque transaction ({@code BEGIN IMMEDIATE}). Sans ce dernier, une
     * transaction qui lit puis écrit échoue aussitôt en {@code SQLITE_BUSY} si une autre a écrit
     * entre-temps, par exemple l'indexation de la connaissance (FR-033 de 002).
     */
    public static String jdbcUrl(Path database) {
        return "jdbc:sqlite:" + database + "?journal_mode=WAL&foreign_keys=true&busy_timeout=5000"
                + "&transaction_mode=IMMEDIATE";
    }

    @Override
    public Set<String> getPropertyNames() {
        return values.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return values.get(propertyName);
    }

    @Override
    public String getName() {
        return "spec-writer-platform";
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }
}
