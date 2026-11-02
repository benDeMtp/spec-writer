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
        String url = "jdbc:sqlite:" + data.resolve("spec-writer.db")
                + "?journal_mode=WAL&foreign_keys=true&busy_timeout=5000";
        int port = PortSelector.choose(Integer.getInteger("specwriter.port", PortSelector.DEFAULT_PORT), LaunchMode.current());
        this.values = Map.of(
                "quarkus.datasource.jdbc.url", url,
                "quarkus.http.port", Integer.toString(port),
                "specwriter.data-dir.resolved", data.toString());
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
