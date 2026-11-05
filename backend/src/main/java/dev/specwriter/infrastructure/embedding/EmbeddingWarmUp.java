package dev.specwriter.infrastructure.embedding;

import dev.specwriter.domain.port.EmbeddingPort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Charge au démarrage, en tâche de fond, le modèle de recherche déjà présent sur le poste : sans
 * cela, il ne l'est qu'à la première indexation, et le premier besoin après le lancement est
 * cherché par les termes seuls (research.md R19, vu en T119). Rien n'est téléchargé ici ; aucune
 * indexation n'est lancée (R20).
 */
@Singleton
public class EmbeddingWarmUp {

    private static final Logger LOG = Logger.getLogger(EmbeddingWarmUp.class);
    private static final EmbeddingPort.Progress NONE = new EmbeddingPort.Progress() {
        @Override
        public void advanced(long done, long total) {
            // pas de téléchargement au démarrage
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    };

    private final EmbeddingPort embedding;
    private final boolean enabled;

    @Inject
    public EmbeddingWarmUp(EmbeddingPort embedding,
            @ConfigProperty(name = "specwriter.embedding.warm-up", defaultValue = "true") boolean enabled) {
        this.embedding = embedding;
        this.enabled = enabled;
    }

    void onStart(@Observes StartupEvent event) {
        start();
    }

    /** Fil du chargement, ou {@code null} si le modèle est absent du poste, déjà prêt ou si c'est désactivé. */
    Thread start() {
        if (!enabled || embedding.state() == EmbeddingPort.State.READY || !embedding.availableLocally()) {
            return null;
        }
        return Thread.ofVirtual().name("embedding-warm-up").start(() -> {
            EmbeddingPort.State state = embedding.prepare(NONE);
            if (state != EmbeddingPort.State.READY) {
                LOG.warnf("Modèle de recherche non chargé au démarrage (%s)", state);
            }
        });
    }
}
