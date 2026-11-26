package fr.kawamind.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 023 (data-model) : statut d'arrivée d'une tâche implémentée et statuts où elle compte comme finie. */
class TaskWorkflowTest {

    static final TaskWorkflow AURORA = TaskWorkflow.of("Réalisée", List.of("En cours de test", "A valider"));

    static void refused(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void leStatutDArriveeEstToujoursFini() {
        assertThat(AURORA.finishedStatuses()).containsExactly("Réalisée", "En cours de test", "A valider");
        assertThat(TaskWorkflow.of("Réalisée", List.of("realisee", "Testée")).finishedStatuses())
                .containsExactly("Réalisée", "Testée");
        assertThat(AURORA.arrivalStatus()).isEqualTo("Réalisée");
    }

    @Test
    void validation() {
        refused(() -> TaskWorkflow.of(" ", List.of()));
        refused(() -> TaskWorkflow.of("x".repeat(101), List.of()));
        refused(() -> TaskWorkflow.of("Réalisée", List.of("Testée", "testee")));
        refused(() -> TaskWorkflow.of("Réalisée", List.of("x".repeat(101))));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add("S" + i);
        }
        refused(() -> TaskWorkflow.of("Réalisée", many));
        assertThat(TaskWorkflow.of("Réalisée", many.subList(0, 49)).finishedStatuses()).hasSize(50);
    }

    @Test
    void statutsFinisEtStatutDUneTache() {
        assertThat(AURORA.finished("Réalisée", "indeterminate")).isTrue();
        assertThat(AURORA.finished("en cours de test", "indeterminate")).isTrue();
        assertThat(AURORA.finished("Fermée", "done")).isTrue();
        assertThat(AURORA.finished("En Cours", "indeterminate")).isFalse();
        assertThat(AURORA.statusOf("Réalisée", "indeterminate")).contains(TaskStatus.DONE);
        assertThat(AURORA.statusOf("En Cours", "indeterminate")).contains(TaskStatus.IN_PROGRESS);
        assertThat(AURORA.statusOf("Prête", "new")).contains(TaskStatus.TODO);
        assertThat(AURORA.statusOf("Inconnu", "autre")).isEqualTo(Optional.empty());
    }

    @Test
    void sansReglageLaCategorieDecide() {
        assertThat(TaskWorkflow.NONE.statusOf("Réalisée", "indeterminate")).contains(TaskStatus.IN_PROGRESS);
        assertThat(TaskWorkflow.NONE.finished("Fermée", "done")).isTrue();
        assertThat(TaskWorkflow.NONE.completion()).isEqualTo(new Target.Category(StatusCategory.DONE));
        assertThat(TaskWorkflow.NONE.configured()).isFalse();
        assertThat(AURORA.completion()).isEqualTo(new Target.Status("Réalisée", AURORA));
        assertThat(AURORA.configured()).isTrue();
    }
}
