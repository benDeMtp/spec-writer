package dev.specwriter.infrastructure.platform;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Le fichier des agents vit dans le dossier de données résolu au démarrage. */
@ApplicationScoped
public class AgentEndpointProducer {

    @ConfigProperty(name = "specwriter.data-dir.resolved")
    String dataDir;
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "47615")
    int port;

    @Produces
    @Singleton
    AgentEndpointFile agentEndpointFile() {
        return new AgentEndpointFile(Path.of(dataDir), () -> port, UserDirectories.fromSystem().posix());
    }
}
