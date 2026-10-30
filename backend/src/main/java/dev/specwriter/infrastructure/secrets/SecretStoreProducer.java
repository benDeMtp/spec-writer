package dev.specwriter.infrastructure.secrets;

import dev.specwriter.domain.port.SecretStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SecretStoreProducer {

    @ConfigProperty(name = "specwriter.secrets.simulated-keychain", defaultValue = "false")
    boolean simulated;

    @Produces
    @ApplicationScoped
    SecretStore secretStore() {
        if (simulated) {
            ProcessMemoryBackend backend = new ProcessMemoryBackend();
            return new KeychainSecretStore(() -> backend, Duration.ofSeconds(5));
        }
        return KeychainSecretStore.system();
    }
}
