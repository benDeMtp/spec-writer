package dev.specwriter.infrastructure.platform;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.Priority;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Empêche le démarrage si un mode de test (GitHub simulé, rejeu du LLM) est activé hors des profils
 * {@code test} et {@code e2e}.
 */
@ApplicationScoped
public class StartupGuard {

    @ConfigProperty(name = "specwriter.github.fake", defaultValue = "false")
    boolean githubFake;

    @ConfigProperty(name = "specwriter.llm.replay-dir")
    Optional<String> replayDir;

    void onStart(@Observes @Priority(1) StartupEvent event) {
        check(ConfigUtils.getProfiles(), githubFake, replayDir);
    }

    static void check(List<String> profiles, boolean githubFake, Optional<String> replayDir) {
        boolean testProfile = profiles.contains("test") || profiles.contains("e2e");
        if (testProfile) {
            return;
        }
        if (githubFake) {
            throw new IllegalStateException(
                    "Démarrage refusé : specwriter.github.fake n'est autorisé qu'avec les profils test ou e2e");
        }
        if (replayDir.filter(s -> !s.isBlank()).isPresent()) {
            throw new IllegalStateException(
                    "Démarrage refusé : specwriter.llm.replay-dir n'est autorisé qu'avec les profils test ou e2e");
        }
    }
}
