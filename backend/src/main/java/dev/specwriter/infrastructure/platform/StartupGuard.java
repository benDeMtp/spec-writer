package dev.specwriter.infrastructure.platform;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.Priority;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Empêche le démarrage si un mode de test (GitHub simulé, rejeu du LLM) est activé hors du dev mode,
 * des tests et du profil {@code e2e} (research.md R17). Le jar installé tourne en mode
 * {@code NORMAL} : y forcer le profil {@code dev} ne suffit pas à activer les simulations.
 */
@ApplicationScoped
public class StartupGuard {

    @ConfigProperty(name = "specwriter.github.fake", defaultValue = "false")
    boolean githubFake;

    @ConfigProperty(name = "specwriter.llm.replay-dir")
    Optional<String> replayDir;

    void onStart(@Observes @Priority(1) StartupEvent event) {
        check(LaunchMode.current(), ConfigUtils.getProfiles(), githubFake, replayDir);
    }

    static void check(LaunchMode mode, List<String> profiles, boolean githubFake, Optional<String> replayDir) {
        if (mode != LaunchMode.NORMAL || profiles.contains("e2e")) {
            return;
        }
        if (githubFake) {
            throw new IllegalStateException(
                    "Démarrage refusé : specwriter.github.fake n'est autorisé qu'en dev mode, en test ou avec le profil e2e");
        }
        if (replayDir.filter(s -> !s.isBlank()).isPresent()) {
            throw new IllegalStateException(
                    "Démarrage refusé : specwriter.llm.replay-dir n'est autorisé qu'en dev mode, en test ou avec le profil e2e");
        }
    }
}
