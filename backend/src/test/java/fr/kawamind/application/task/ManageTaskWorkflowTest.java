package fr.kawamind.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.task.KnownStatus;
import fr.kawamind.support.JiraWorld;
import fr.kawamind.support.InMemoryTaskWorkflows;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T019 (023, US3, contracts/rest-api.md) : lire, enregistrer et retirer le suivi des tâches d'un projet. */
class ManageTaskWorkflowTest {

    static final UUID P = Project.DEFAULT_ID;

    final JiraWorld w = new JiraWorld();
    final InMemoryTaskWorkflows workflows = new InMemoryTaskWorkflows();
    ManageTaskWorkflow manage;

    @BeforeEach
    void setUp() {
        manage = new ManageTaskWorkflow(w.projects, workflows, w.clock);
        workflows.known.put(P, List.of(new KnownStatus("Prête", "new", true),
                new KnownStatus("En Cours", "indeterminate", true), new KnownStatus("Réalisée", "indeterminate", true),
                new KnownStatus("Fermée", "done", true)));
    }

    static void refused(Runnable r, ErrorCode code, String message) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            if (message != null) {
                assertThat(e.userMessage()).isEqualTo(message);
            }
        });
    }

    @Test
    void sansReglageLesStatutsConnusSeulement() {
        ManageTaskWorkflow.View view = manage.view(P);
        assertThat(view.arrivalStatus()).isNull();
        assertThat(view.finishedStatuses()).isEmpty();
        assertThat(view.known()).extracting(KnownStatus::name).containsExactly("Prête", "En Cours", "Réalisée", "Fermée");
    }

    @Test
    void enregistreAvecUnStatutPlusVuDansLIndex() {
        ManageTaskWorkflow.View view = manage.save(P, "Réalisée", List.of("Déployable"));
        assertThat(view.arrivalStatus()).isEqualTo("Réalisée");
        assertThat(view.finishedStatuses()).containsExactly("Réalisée", "Déployable");
        assertThat(view.known()).last().isEqualTo(new KnownStatus("Déployable", null, false));
        assertThat(manage.view(P).finishedStatuses()).containsExactly("Réalisée", "Déployable");
    }

    @Test
    void unStatutAFaireNEstPasUnStatutDArrivee() {
        refused(() -> manage.save(P, "prête", List.of()), ErrorCode.VALIDATION_FAILED,
                "Le statut d'arrivée d'une tâche implémentée ne peut pas être un statut « à faire » (Prête).");
        refused(() -> manage.save(P, "", List.of()), ErrorCode.VALIDATION_FAILED, null);
        assertThat(manage.view(P).arrivalStatus()).isNull();
    }

    @Test
    void retirerLeReglage() {
        manage.save(P, "Réalisée", List.of());
        manage.remove(P);
        assertThat(manage.view(P).arrivalStatus()).isNull();
    }

    @Test
    void projetInconnu() {
        refused(() -> manage.view(UUID.randomUUID()), ErrorCode.PROJECT_NOT_FOUND, null);
        refused(() -> manage.save(UUID.randomUUID(), "Réalisée", List.of()), ErrorCode.PROJECT_NOT_FOUND, null);
        refused(() -> manage.remove(UUID.randomUUID()), ErrorCode.PROJECT_NOT_FOUND, null);
    }
}
