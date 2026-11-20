package fr.kawamind.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import fr.kawamind.domain.sync.SyncInterval;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T028 : la tâche planifiée réveille la synchronisation des sources échues (017, research.md R4). */
@QuarkusTest
@TestProfile(SourceSyncJobIT.SchedulerOn.class)
@org.junit.jupiter.api.parallel.Isolated
class SourceSyncJobIT {

    /** Le scheduler, coupé en test, est réactivé avec un réveil rapide. */
    public static class SchedulerOn implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.scheduler.enabled", "true", "specwriter.knowledge.auto-sync.every", "1s",
                    "specwriter.knowledge.auto-sync.delayed", "1s");
        }
    }

    @Inject
    Scheduler scheduler;
    @Inject
    SourceSyncRepository syncs;
    @Inject
    ProjectRepository projects;
    @Inject
    DocSourceRepository docSources;

    @Test
    void uneSourceEchueEstSynchroniseeSansAttendreSonDelai() throws Exception {
        assertThat(scheduler.getScheduledJobs()).extracting(j -> j.getId()).contains(SourceSyncJob.IDENTITY);
        UUID project = UUID.randomUUID();
        projects.save(Project.create(project, ProjectName.of("Planifié " + project.toString().substring(0, 8)), null,
                null, Instant.now()));
        Path folder = Files.createTempDirectory("source-sync-it");
        try {
            UUID doc = UUID.randomUUID();
            docSources.save(DocSource.create(doc, project, DocSource.Kind.DOCUMENTS, folder.toString()));
            syncs.setInterval(project, doc.toString(), SyncInterval.D1);
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (syncs.find(project, doc.toString()).map(s -> s.last() == null).orElse(true)
                    && System.nanoTime() < deadline) {
                Thread.sleep(200);
            }
            assertThat(syncs.find(project, doc.toString())).get().satisfies(s -> assertThat(s.last()).isNotNull());
        } finally {
            projects.delete(project);
            Files.deleteIfExists(folder);
        }
    }
}
