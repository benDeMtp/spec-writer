package fr.kawamind.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.sources.PerimeterSnapshot;
import fr.kawamind.domain.sources.Qualification;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.ProposedTasks;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskOrigin;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskProposal.Basing;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.support.TaskWorld;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T018 (015, US1, FR-001 à FR-003, FR-006, R8, R10) : proposition de tâches. */
class ProposeTasksTest {

    final TaskWorld w = new TaskWorld();
    ProposeTasks propose;
    Session session;

    static ProposedTask uc(String title) {
        return new ProposedTask(title, "Résultat " + title, Basis.useCase("UC-01"), List.of(), false);
    }

    @BeforeEach
    void setUp() {
        propose = new ProposeTasks(w.desk, w.versions, w.perimeters, w.taskLlm, w.policy);
        session = w.linkedSession();
        w.taskLlm.answer = r -> new ProposedTasks(List.of(uc("A"), uc("B"), uc("C")));
    }

    static void refused(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void sansTicketLieAucuneProposition() {
        Session alone = w.generatedSession();
        refused(() -> propose.run(alone.id()), ErrorCode.NO_LINKED_TICKET);
        assertThat(w.taskLlm.requests).isEmpty();
    }

    @Test
    void unTicketSousTacheNePortePasDeSousTaches() {
        w.tracker.parents.put("FACT-120", true);
        refused(() -> propose.run(session.id()), ErrorCode.PARENT_IS_SUBTASK);
    }

    @Test
    void sansPerimetreLesCasDUsageSansRienEnvoyerAJira() {
        TaskProposal p = propose.run(session.id());
        assertThat(p.basing()).isEqualTo(Basing.USE_CASES);
        assertThat(p.tasks()).extracting(ProposedTask::title).containsExactly("A", "B", "C");
        assertThat(w.taskLlm.requests.get(0).useCases()).extracting(u -> u.code()).containsExactly("UC-01");
        assertThat(w.taskLlm.requests.get(0).bounds()).isEqualTo(new TaskProposal.Bounds(3, 8));
        assertThat(w.tasks.proposal(session.id())).contains(p);
        assertThat(w.tracker.writes).isEmpty();
    }

    @Test
    void avecUnPerimetreLesModulesTouches() {
        w.perimeters.snapshots.put(session.id(), new PerimeterSnapshot(List.of(
                new PerimeterSnapshot.Entry("facturation-api", "modules/facturation", "rv", Qualification.IMPACTED,
                        "Calcule", List.of(), List.of()),
                new PerimeterSnapshot.Entry("portail", "modules/portail", "rv", Qualification.NOT_IMPACTED, "Rien",
                        List.of(), List.of())), false));
        w.taskLlm.answer = r -> new ProposedTasks(List.of(
                new ProposedTask("A", "R", Basis.module("facturation-api"), List.of(), false),
                new ProposedTask("B", "R", Basis.module("facturation-api"), List.of(0), false),
                new ProposedTask("C", "R", Basis.module("facturation-api"), List.of(), false)));
        TaskProposal p = propose.run(session.id());
        assertThat(p.basing()).isEqualTo(Basing.MODULES);
        assertThat(w.taskLlm.requests.get(0).modules()).extracting(m -> m.name()).containsExactly("facturation-api");
    }

    @Test
    void lesSousTachesExistantesSontRepriseesSansEtreRecreees() {
        w.tracker.addSubtask("FACT-120", "FACT-121", "Maquette", "done");
        w.taskLlm.answer = r -> new ProposedTasks(List.of(uc("A")));
        propose.run(session.id());
        List<SpecTask> tasks = w.tasks.tasks(session.id());
        assertThat(tasks).singleElement().satisfies(t -> {
            assertThat(t.origin()).isEqualTo(TaskOrigin.JIRA);
            assertThat(t.jiraKey()).isEqualTo("FACT-121");
            assertThat(t.status()).isEqualTo(TaskStatus.DONE);
        });
        assertThat(w.taskLlm.requests.get(0).existingTitles()).containsExactly("Maquette");
        assertThat(w.taskLlm.requests.get(0).bounds()).isEqualTo(new TaskProposal.Bounds(1, 7));
        propose.run(session.id());
        assertThat(w.tasks.tasks(session.id())).hasSize(1);
    }

    @Test
    void uneReponseHorsDesReglesOuInvalideDonneTasksUnavailable() {
        w.taskLlm.answer = r -> new ProposedTasks(List.of(uc("A")));
        refused(() -> propose.run(session.id()), ErrorCode.TASKS_UNAVAILABLE);
        w.taskLlm.answer = r -> {
            throw new DomainException(ErrorCode.LLM_INVALID_OUTPUT);
        };
        refused(() -> propose.run(session.id()), ErrorCode.TASKS_UNAVAILABLE);
        w.taskLlm.answer = r -> {
            throw new DomainException(ErrorCode.QUOTA_EXHAUSTED);
        };
        refused(() -> propose.run(session.id()), ErrorCode.QUOTA_EXHAUSTED);
        assertThat(w.tasks.proposal(session.id())).isEmpty();
    }

    @Test
    void uneNouvellePropositionRemplaceLaPrecedente() {
        propose.run(session.id());
        w.taskLlm.answer = r -> new ProposedTasks(List.of(uc("D"), uc("E"), uc("F")));
        propose.run(session.id());
        assertThat(w.tasks.proposal(session.id()).orElseThrow().tasks()).extracting(ProposedTask::title)
                .containsExactly("D", "E", "F");
    }
}
