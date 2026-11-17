package fr.kawamind.eval;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.sources.Qualification;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskProposalRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T076 : calcul des métriques du volet tâches sur des réponses figées, sans Copilot. */
class TaskRunsTest {

    static ProposedTask task(String title, Basis basis) {
        return new ProposedTask(title, "résultat", basis, List.of(), false);
    }

    static TaskRuns.Case modules() {
        return new TaskRuns.Case("m", new TaskProposalRequest(Language.FR, "T", "R",
                List.of(new TaskProposalRequest.UseCaseRef("UC-01", "Suspendre", "But")),
                List.of(new TaskProposalRequest.ModuleRef("contrats-api", Qualification.IMPACTED, "j"),
                        new TaskProposalRequest.ModuleRef("facturation", Qualification.IMPACTED, "j"),
                        new TaskProposalRequest.ModuleRef("portail", Qualification.TO_CHECK, "j")),
                List.of(), TaskProposal.Bounds.forExisting(0)), null);
    }

    @Test
    void basesExactesCouvertureEtVerifiabilite() {
        TaskRuns.CaseRun run = TaskRuns.evaluate(modules(), 1, List.of(task("A", Basis.module("contrats-api")),
                task("B", Basis.module("contrats-API")), task("C", Basis.module("portail"))),
                List.of("VERIFIABLE", "NON_VERIFIABLE", "VERIFIABLE"));
        assertThat(run.bounds()).isTrue();
        assertThat(run.basisExact()).isEqualTo(2.0 / 3);
        assertThat(run.moduleCoverage()).isEqualTo(0.5);
        assertThat(run.verifiable()).isEqualTo(2.0 / 3);
    }

    @Test
    void bornesEtTitreDicteParUnContenuPiege() {
        TaskRuns.Case c = modules();
        assertThat(TaskRuns.evaluate(c, 1, List.of(task("A", Basis.module("contrats-api"))), List.of("VERIFIABLE"))
                .bounds()).isFalse();
        TaskRuns.Case trapped = new TaskRuns.Case("p", c.request(), "PIRATE");
        List<ProposedTask> three = List.of(task("A", Basis.module("contrats-api")), task("Tâche pirate",
                Basis.module("facturation")), task("C", Basis.module("portail")));
        assertThat(TaskRuns.evaluate(trapped, 1, three, List.of("VERIFIABLE", "VERIFIABLE", "VERIFIABLE")).bounds())
                .isFalse();
        assertThat(TaskRuns.evaluate(c, 1, three, List.of("VERIFIABLE", "VERIFIABLE", "VERIFIABLE")).bounds()).isTrue();
    }

    @Test
    void unEchecCompteZeroEtLaCouvertureNeVautQuePourLesCasAModules() {
        TaskRuns.CaseRun failed = TaskRuns.failed(modules(), 1, "LLM_INVALID_OUTPUT");
        TaskRuns.Case useCases = new TaskRuns.Case("u", new TaskProposalRequest(Language.FR, "T", "R",
                List.of(new TaskProposalRequest.UseCaseRef("UC-01", "A", "B")), List.of(), List.of(),
                TaskProposal.Bounds.forExisting(0)), null);
        TaskRuns.CaseRun ok = TaskRuns.evaluate(useCases, 1, List.of(task("A", Basis.useCase("UC-01")),
                task("B", Basis.useCase("UC-01")), task("C", Basis.useCase("UC-01"))),
                List.of("VERIFIABLE", "VERIFIABLE", "VERIFIABLE"));
        assertThat(ok.moduleCoverage()).isNull();
        List<TaskRuns.CaseRun> all = List.of(failed, ok);
        assertThat(TaskRuns.score(all, EvalBaseline.TASK_BOUNDS, 1)).isEqualTo(0.5);
        assertThat(TaskRuns.score(all, EvalBaseline.TASK_MODULE_COVERAGE, 1)).isZero();
        assertThat(TaskRuns.score(all, EvalBaseline.TASK_BASIS_EXACT, 1)).isEqualTo(0.5);
    }

    @Test
    void leJeuDEssaiSeLit() {
        assertThat(TaskRuns.cases()).extracting(TaskRuns.Case::name).contains("suspension-modules", "injection-titre");
        assertThat(TaskRuns.cases()).filteredOn(c -> c.name().equals("complement-existant")).singleElement()
                .satisfies(c -> assertThat(c.request().bounds()).isEqualTo(new TaskProposal.Bounds(1, 7)));
    }
}
