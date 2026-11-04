package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.ContextQuestionRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.ContextQuestion;
import dev.specwriter.domain.project.ContextSheet;
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

    @Inject
    ContextQuestionRepository questions;

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
                .withContext(ContextSheet.FUNCTIONAL, ProjectContext.generated("## Acteurs\n- gestionnaire de paie", T0.plusSeconds(5)))
                .withContext(ContextSheet.TECHNICAL, ProjectContext.edited("## Architecture\n- Quarkus et SQLite", T0.plusSeconds(6)));
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

        // questions répondues ou passées, numérotées dans le projet (V9)
        assertThat(questions.nextNumber(p.id())).isEqualTo(1);
        ContextQuestion q1 = ContextQuestion.recorded(UUID.randomUUID(), p.id(), 1, ContextSheet.TECHNICAL, "Quelle base ?",
                "Les sources se contredisent", "SQLite", false, T0, T0.plusSeconds(20));
        ContextQuestion q2 = ContextQuestion.recorded(UUID.randomUUID(), p.id(), 2, ContextSheet.FUNCTIONAL, "Quelle vision ?",
                "", null, true, T0, T0.plusSeconds(21));
        questions.save(q2);
        questions.save(q1);
        assertThat(questions.list(p.id())).containsExactly(q1, q2);
        assertThat(questions.nextNumber(p.id())).isEqualTo(3);
        ContextQuestion changed = q2.reanswered("Simplifier la paie", false, T0.plusSeconds(30));
        questions.save(changed);
        assertThat(questions.find(p.id(), 2)).contains(changed);

        // supprimer le projet supprime ses sources et ses questions (ON DELETE CASCADE)
        projects.delete(p.id());
        created = null;
        assertThat(sources.list(p.id())).isEmpty();
        assertThat(questions.list(p.id())).isEmpty();
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
