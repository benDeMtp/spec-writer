package fr.kawamind.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueTrackerException.Kind;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskCreation;
import fr.kawamind.domain.task.TaskOrigin;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.support.TaskWorld;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T019 (015, FR-005 à FR-008, R10) : validation en sous-tâches Jira, sans doublon ni suppression. */
class ValidateTasksTest {

    final TaskWorld w = new TaskWorld();
    final ReadTasks read = new ReadTasks(w.desk, w.connections);
    final ValidateTasks validate = new ValidateTasks(w.desk, read);
    Session session;

    @BeforeEach
    void setUp() {
        session = w.linkedSession();
        w.tasks.saveProposal(session.id(), new TaskProposal(TaskProposal.Basing.USE_CASES, List.of(
                new ProposedTask("Exposer l'endpoint", "POST renvoie 201", Basis.useCase("UC-01"), List.of(), false),
                new ProposedTask("Écran de rétractation", "Le bouton apparaît", Basis.useCase("UC-01"), List.of(0), false),
                new ProposedTask("Ajoutée", "Le mail part", Basis.NONE, List.of(), true))), Instant.EPOCH);
    }

    @Test
    void chaqueTacheDevientUneSousTacheDansLOrdre() {
        TasksView view = validate.run(session.id());
        assertThat(w.tracker.calls).filteredOn(c -> c.startsWith("createSubtask")).containsExactly(
                "createSubtask FACT-120 Exposer l'endpoint", "createSubtask FACT-120 Écran de rétractation",
                "createSubtask FACT-120 Ajoutée");
        assertThat(view.tasks()).extracting(SpecTask::jiraKey).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(view.tasks()).extracting(SpecTask::status).containsOnly(TaskStatus.TODO);
        assertThat(view.tasks()).extracting(SpecTask::origin).containsExactly(TaskOrigin.PROPOSED, TaskOrigin.PROPOSED,
                TaskOrigin.MANUAL);
        assertThat(view.tasks().get(1).dependsOn()).containsExactly(view.tasks().get(0).id());
        assertThat(view.creationProblem()).isNull();
        assertThat(view.done()).isZero();
        assertThat(view.total()).isEqualTo(3);
        assertThat(view.hasProposal()).isFalse();
        assertThat(w.tracker.comments).isEmpty();
        assertThat(w.tracker.issues.get("FACT-120")).isNotNull();
    }

    @Test
    void uneErreurAuMilieuLaisseLesSuivantesACreerPuisLaRelanceNeCreePasDeDoublon() {
        w.tracker.failCreateSubtaskNumber = 2;
        TasksView view = validate.run(session.id());
        assertThat(view.creationProblem()).contains("« Écran de rétractation »");
        assertThat(view.tasks()).extracting(SpecTask::creation).containsExactly(TaskCreation.CREATED,
                TaskCreation.NOT_CREATED, TaskCreation.NOT_CREATED);
        TasksView retried = validate.createRemaining(session.id());
        assertThat(retried.creationProblem()).isNull();
        assertThat(retried.tasks()).extracting(SpecTask::creation).containsOnly(TaskCreation.CREATED);
        assertThat(w.tracker.children.get("FACT-120")).hasSize(3);
    }

    @Test
    void uneSousTacheCreeeMaisPasEnregistreeEstReprise() {
        w.tracker.addSubtask("FACT-120", "FACT-130", "Exposer l'endpoint", "indeterminate");
        TasksView view = validate.run(session.id());
        assertThat(view.tasks().get(0).jiraKey()).isEqualTo("FACT-130");
        assertThat(view.tasks().get(0).status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(w.tracker.children.get("FACT-120")).hasSize(3);
    }

    @Test
    void sansTypeSousTacheRienNEstCree() {
        w.tracker.subtaskType = Optional.empty();
        assertThatThrownBy(() -> validate.run(session.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_SUBTASK_TYPE));
        assertThat(w.tasks.tasks(session.id())).isEmpty();
        assertThat(w.tasks.proposal(session.id())).isPresent();
    }

    @Test
    void uneTacheVideEstRefusee() {
        w.tasks.saveProposal(session.id(), new TaskProposal(TaskProposal.Basing.USE_CASES,
                List.of(new ProposedTask("", "R", Basis.NONE, List.of(), true))), Instant.EPOCH);
        assertThatThrownBy(() -> validate.run(session.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void jiraInjoignableALaCreationEstSignale() {
        w.tracker.failCreateSubtaskNumber = 1;
        w.tracker.subtaskCreateFailureAt = Kind.UNREACHABLE;
        TasksView view = validate.run(session.id());
        assertThat(view.creationProblem()).contains("Le serveur Jira ne répond pas");
        assertThat(w.tracker.writes).isEmpty();
    }

    // 024 (US1, research R3) : le label du projet sur chaque sous-tâche, créée ou reprise

    void label() {
        w.jiraLabels.save(session.projectId(), fr.kawamind.domain.jira.JiraLabel.of("spec-writer"), w.clock.now());
    }

    @Test
    void lesSousTachesCreeesPortentLeLabel() {
        label();
        TasksView view = validate.run(session.id());
        assertThat(view.tasks()).allSatisfy(t -> {
            assertThat(w.tracker.tracked.get(t.jiraKey()).labels()).containsExactly("spec-writer");
            assertThat(w.ticketLabels.has(session.projectId(), t.jiraKey(), "spec-writer")).isTrue();
        });
        assertThat(view.creationProblem()).isNull();
    }

    @Test
    void uneSousTacheRepriseRecoitLeLabel() {
        label();
        w.tracker.addSubtask("FACT-120", "FACT-130", "Exposer l'endpoint", "new");
        validate.run(session.id());
        assertThat(w.tracker.calls).contains("label FACT-130 spec-writer");
        assertThat(w.tracker.tracked.get("FACT-130").labels()).containsExactly("spec-writer");
    }

    @Test
    void unLabelRefuseEstDitSansArreterLaCreation() {
        label();
        w.tracker.addSubtask("FACT-120", "FACT-130", "Exposer l'endpoint", "new");
        w.tracker.labelFailure = fr.kawamind.domain.jira.IssueTrackerException.Kind.REJECTED;
        TasksView view = validate.run(session.id());
        assertThat(view.tasks()).extracting(SpecTask::jiraKey).doesNotContainNull();
        assertThat(view.creationProblem()).startsWith("Le label spec-writer n'a pas pu être posé sur FACT-130");
    }
}
