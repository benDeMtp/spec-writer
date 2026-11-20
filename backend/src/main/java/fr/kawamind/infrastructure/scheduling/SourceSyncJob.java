package fr.kawamind.infrastructure.scheduling;

import fr.kawamind.application.knowledge.AutoSync;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Réveil de la synchronisation automatique des sources (017, research.md R4) : la tâche planifiée
 * de quarkus-scheduler ne décide rien, le cas d'usage {@link AutoSync} choisit les sources échues.
 * Le premier passage, peu après le démarrage, rattrape les échéances dépassées (FR-011).
 */
@ApplicationScoped
public class SourceSyncJob {

    public static final String IDENTITY = "source-sync";

    @Inject
    AutoSync autoSync;

    @Scheduled(identity = IDENTITY, every = "${specwriter.knowledge.auto-sync.every}",
            delayed = "${specwriter.knowledge.auto-sync.delayed}", concurrentExecution = ConcurrentExecution.SKIP)
    @RunOnVirtualThread
    void tick() {
        autoSync.tick();
    }
}
