package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import fr.kawamind.domain.sync.SourceChange;
import fr.kawamind.domain.sync.SourceSync;
import fr.kawamind.domain.sync.SourceSyncResult;
import fr.kawamind.domain.sync.SyncInterval;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T027 : table {@code source_sync} (V28, 017). */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class SourceSyncSqliteRepositoryIT {

    static final Instant T0 = Instant.parse("2026-11-19T10:00:00Z");

    @Inject
    SourceSyncRepository syncs;
    @Inject
    ProjectRepository projects;
    @Inject
    DocSourceRepository docSources;

    UUID project;

    @BeforeEach
    void projet() {
        project = UUID.randomUUID();
        projects.save(Project.create(project, ProjectName.of("Synchro " + project.toString().substring(0, 8)), null, null, T0));
    }

    @AfterEach
    void nettoyer() {
        projects.delete(project);
    }

    @Test
    void delaiPuisBilansReussiEtEnEchec() {
        assertThat(syncs.find(project, "JIRA")).isEmpty();
        syncs.setInterval(project, "JIRA", SyncInterval.H4);
        assertThat(syncs.find(project, "JIRA")).get().satisfies(s -> {
            assertThat(s.interval()).isEqualTo(SyncInterval.H4);
            assertThat(s.last()).isNull();
        });
        assertThat(syncs.scheduled()).extracting(SourceSync::projectId).contains(project);

        syncs.record(project, "JIRA", SourceSyncResult.success(T0, new SourceChange(1, 2, 3)));
        syncs.record(project, "JIRA", SourceSyncResult.failure(T0.plusSeconds(60), "UNREACHABLE"));
        SourceSync s = syncs.find(project, "JIRA").orElseThrow();
        assertThat(s.interval()).isEqualTo(SyncInterval.H4);
        assertThat(s.last().outcome()).isEqualTo(SourceSyncResult.Outcome.ERROR);
        assertThat(s.last().errorCode()).isEqualTo("UNREACHABLE");
        assertThat(s.last().at()).isEqualTo(T0.plusSeconds(60));
        assertThat(s.lastSuccessAt()).isEqualTo(T0);

        syncs.record(project, "JIRA", SourceSyncResult.success(T0.plusSeconds(120), new SourceChange(0, 1, 0)));
        s = syncs.find(project, "JIRA").orElseThrow();
        assertThat(s.last().change()).isEqualTo(new SourceChange(0, 1, 0));
        assertThat(s.last().errorCode()).isNull();
        assertThat(s.lastSuccessAt()).isEqualTo(T0.plusSeconds(120));

        syncs.setInterval(project, "JIRA", SyncInterval.OFF);
        assertThat(syncs.scheduled()).extracting(SourceSync::projectId).doesNotContain(project);
        assertThat(syncs.of(project)).hasSize(1);
    }

    @Test
    void unBilanSansDelaiPuisRetraitDeLaSourceEtDuProjet() {
        UUID doc = UUID.randomUUID();
        docSources.save(DocSource.create(doc, project, DocSource.Kind.URL, "https://doc.example.com/" + doc));
        syncs.record(project, doc.toString(), SourceSyncResult.success(T0, SourceChange.NONE));
        assertThat(syncs.find(project, doc.toString())).get().extracting(SourceSync::interval).isEqualTo(SyncInterval.OFF);
        docSources.delete(doc);
        assertThat(syncs.find(project, doc.toString())).isEmpty();

        syncs.setInterval(project, "ARCHITECTURE", SyncInterval.D1);
        projects.delete(project);
        assertThat(syncs.of(project)).isEmpty();
    }
}
