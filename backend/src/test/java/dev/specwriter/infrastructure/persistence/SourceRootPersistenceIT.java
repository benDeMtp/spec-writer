package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SourceRootRepository;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.domain.sources.SourceRoot;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Racines de sources en base (013, T011). */
@QuarkusTest
class SourceRootPersistenceIT {

    static final Instant T0 = Instant.parse("2026-11-11T09:00:00Z");

    @Inject
    ProjectRepository projects;

    @Inject
    SourceRootRepository roots;

    UUID created;

    @AfterEach
    void cleanUp() {
        if (created != null) {
            projects.delete(created);
        }
    }

    @Test
    void racinesRelueesALIdentiqueDansLOrdreEtSupprimeesAvecLeProjet() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Rv " + UUID.randomUUID()), null, null, T0);
        projects.save(p);
        created = p.id();
        SourceRoot rv = roots.save(new SourceRoot(UUID.randomUUID(), p.id(), "/src/rv",
                ExclusionPatterns.of(List.of("legacy/**", "**/*.generated.java")), T0));
        SourceRoot pipeline = roots.save(new SourceRoot(UUID.randomUUID(), p.id(), "/src/pipeline", ExclusionPatterns.NONE, T0));

        assertThat(roots.list(p.id())).containsExactly(rv, pipeline);
        assertThat(roots.find(p.id(), rv.id())).contains(rv);
        assertThat(roots.find(UUID.randomUUID(), rv.id())).isEmpty();

        roots.delete(UUID.randomUUID(), rv.id());
        assertThat(roots.list(p.id())).hasSize(2);
        roots.delete(p.id(), rv.id());
        assertThat(roots.list(p.id())).containsExactly(pipeline);

        projects.delete(p.id());
        created = null;
        assertThat(roots.list(p.id())).isEmpty();
    }
}
