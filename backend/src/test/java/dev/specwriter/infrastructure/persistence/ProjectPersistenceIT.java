package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
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
