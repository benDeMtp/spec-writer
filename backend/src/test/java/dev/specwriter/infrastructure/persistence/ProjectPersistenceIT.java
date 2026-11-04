package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.project.ProjectName;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Projets et projet courant en base (T009, FR-005). */
@QuarkusTest
class ProjectPersistenceIT {

    static final Instant T0 = Instant.parse("2026-11-03T09:00:00Z");

    @Inject
    ProjectRepository projects;

    @Inject
    DocSourceRepository sources;


    UUID created;

    @AfterEach
    void cleanUp() {
        projects.saveCurrentProjectId(Project.DEFAULT_ID);
        if (created != null) {
            projects.delete(created);
        }
    }

    @Test
    void projetParDefautPresentApresLaMigration() {
        assertThat(projects.find(Project.DEFAULT_ID)).hasValueSatisfying(p -> {
            assertThat(p.name().value()).isEqualTo(Project.DEFAULT_NAME);
            assertThat(p.jiraProjectKey()).isNull();
        });
        assertThat(projects.findByNameKey("projet par défaut")).isPresent();
    }

    @Test
    void contexteEtSourcesRelusALIdentique() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Paie " + UUID.randomUUID()), null, null, T0)
                .withContext(ProjectContext.edited("## Acteurs\n- gestionnaire de paie\n\n## Architecture\n- Quarkus",
                        T0.plusSeconds(6)));
        created = p.id();
        projects.save(p);
        assertThat(projects.find(p.id())).contains(p);

        DocSource first = DocSource.create(UUID.randomUUID(), p.id(), DocSource.Kind.URL, "https://docs.example.com/paie/");
        DocSource second = DocSource.create(UUID.randomUUID(), p.id(), DocSource.Kind.URL, "http://localhost:8080/");
        sources.save(first);
        sources.save(second);
        DocSource read = first.read(T0.plusSeconds(10), new DocSource.ReadSummary(30, 1, false));
        sources.save(read);
        assertThat(sources.list(p.id())).containsExactly(read, second);
        assertThat(sources.find(first.id())).contains(read);
        sources.delete(second.id());
        assertThat(sources.list(p.id())).containsExactly(read);

        // supprimer le projet supprime ses sources (ON DELETE CASCADE)
        projects.delete(p.id());
        created = null;
        assertThat(sources.list(p.id())).isEmpty();
    }

    @Test
    void projetReluALIdentiqueEtProjetCourantConserve() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), "Factures et avoirs",
                JiraProjectKey.ofNullable("FACT"), T0);
        created = p.id();
        projects.save(p);
        assertThat(projects.find(p.id())).contains(p);
        assertThat(projects.findByNameKey("facturation")).contains(p);
        assertThat(projects.list()).extracting(Project::nameKey).containsSubsequence("facturation", "projet par défaut");
        assertThat(projects.count()).isGreaterThanOrEqualTo(2);

        projects.saveCurrentProjectId(p.id());
        assertThat(projects.currentProjectId()).contains(p.id());

        // projet supprimé : plus de projet courant enregistré (ON DELETE SET NULL)
        projects.delete(p.id());
        created = null;
        assertThat(projects.find(p.id())).isEmpty();
        assertThat(projects.currentProjectId()).isEmpty();
    }
}
