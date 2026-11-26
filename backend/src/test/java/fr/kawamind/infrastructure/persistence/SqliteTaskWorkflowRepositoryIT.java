package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.TaskWorkflowRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import fr.kawamind.domain.task.KnownStatus;
import fr.kawamind.domain.task.TaskWorkflow;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T005 (023, V35, research R2) : réglage du suivi des tâches et statuts connus de l'index. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class SqliteTaskWorkflowRepositoryIT {

    static final Instant T0 = Instant.parse("2026-11-26T09:00:00Z");

    @Inject
    TaskWorkflowRepository workflows;
    @Inject
    ProjectRepository projects;
    @Inject
    KnowledgeIndex index;

    UUID project;
    UUID other;

    @BeforeEach
    void projets() {
        project = UUID.randomUUID();
        other = UUID.randomUUID();
        projects.save(Project.create(project, ProjectName.of("Workflow " + project.toString().substring(0, 8)), null,
                null, T0));
        projects.save(Project.create(other, ProjectName.of("Autre " + other.toString().substring(0, 8)), null, null,
                T0));
    }

    @AfterEach
    void nettoyer() {
        projects.delete(project);
        projects.delete(other);
    }

    void ticket(UUID p, String key, String status, String category) {
        index.save(KnowledgeItem.ticket(p, key, "T", "", status, category, "Sous-tâche", T0, T0), null, null);
    }

    @Test
    void enregistreRemplaceEtRetire() {
        assertThat(workflows.find(project)).isEmpty();
        TaskWorkflow aurora = TaskWorkflow.of("Réalisée", List.of("En cours de test", "A valider"));
        workflows.save(project, aurora, T0);
        assertThat(workflows.find(project)).contains(aurora);
        TaskWorkflow other2 = TaskWorkflow.of("Testée", List.of());
        workflows.save(project, other2, T0.plusSeconds(1));
        assertThat(workflows.find(project)).contains(other2);
        workflows.delete(project);
        assertThat(workflows.find(project)).isEmpty();
    }

    @Test
    void statutsConnusUneFoisClassesParCategoriePuisParNom() {
        QuarkusTransaction.requiringNew().run(() -> {
            ticket(project, "W-1", "Fermée", "done");
            ticket(project, "W-2", "En Cours", "indeterminate");
            ticket(project, "W-3", "en cours", "indeterminate");
            ticket(project, "W-4", "En Cours", "indeterminate");
            ticket(project, "W-5", "Réalisée", "indeterminate");
            ticket(project, "W-6", "Prête", "new");
            ticket(project, "W-7", "Créée", "new");
            ticket(other, "X-1", "Déployable", "indeterminate");
        });
        assertThat(workflows.knownStatuses(project)).containsExactly(
                new KnownStatus("Créée", "new", true), new KnownStatus("Prête", "new", true),
                new KnownStatus("En Cours", "indeterminate", true), new KnownStatus("Réalisée", "indeterminate", true),
                new KnownStatus("Fermée", "done", true));
    }
}
