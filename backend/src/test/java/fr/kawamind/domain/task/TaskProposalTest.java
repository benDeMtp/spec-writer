package fr.kawamind.domain.task;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.task.TaskProposal.Basing;
import fr.kawamind.domain.task.TaskProposal.Bounds;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T016 (015, R8) : bornes et règles de la proposition de tâches. */
class TaskProposalTest {

    static ProposedTask uc(String title, String code, Integer... deps) {
        return new ProposedTask(title, "Résultat de " + title, Basis.useCase(code), List.of(deps), false);
    }

    static TaskProposal of(ProposedTask... tasks) {
        return new TaskProposal(Basing.USE_CASES, List.of(tasks));
    }

    static final Set<String> UC = Set.of("UC-01", "UC-02");

    @Test
    void lesBornesDependentDesTachesExistantes() {
        assertThat(Bounds.forExisting(0)).isEqualTo(new Bounds(3, 8));
        assertThat(Bounds.forExisting(3)).isEqualTo(new Bounds(1, 5));
        assertThat(Bounds.forExisting(9)).isEqualTo(new Bounds(1, 1));
    }

    @Test
    void uneReponseConformeEstAcceptee() {
        TaskProposal p = of(uc("A", "UC-01"), uc("B", "UC-02", 0), uc("C", "UC-01", 0, 1));
        assertThat(p.modelProblems(Bounds.forExisting(0), Set.of(), UC, Set.of())).isEmpty();
    }

    @Test
    void leNombreHorsBornesEstRefuse() {
        assertThat(of(uc("A", "UC-01"), uc("B", "UC-02")).modelProblems(Bounds.forExisting(0), Set.of(), UC, Set.of()))
                .containsExactly("Proposez de 3 à 8 tâches, pas 2.");
    }

    @Test
    void formeBaseEtTitresSontVerifies() {
        TaskProposal p = of(new ProposedTask("", "R", Basis.useCase("UC-01"), List.of(), false),
                new ProposedTask("B", "x".repeat(301), Basis.useCase("UC-09"), List.of(1), false),
                uc("Existante", "UC-01"), uc("b", "UC-02"));
        assertThat(p.modelProblems(new Bounds(1, 8), Set.of(), UC, Set.of("existante"))).containsExactly(
                "Tâche 1 : le titre fait de 1 à 120 caractères.",
                "Tâche 2 : le résultat attendu fait de 1 à 300 caractères.",
                "Tâche 2 : un préalable doit désigner une tâche qui la précède.",
                "Tâche 2 : la base doit être un code de cas d'usage cité tel quel.",
                "Tâche 3 : ce titre existe déjà.",
                "Tâche 4 : ce titre existe déjà.");
    }

    @Test
    void avecDesModulesLaBaseEstUnModule() {
        TaskProposal p = new TaskProposal(Basing.MODULES, List.of(
                new ProposedTask("A", "R", Basis.module("facturation-api"), List.of(), false),
                new ProposedTask("B", "R", Basis.module("inconnu"), List.of(), false)));
        assertThat(p.modelProblems(new Bounds(1, 8), Set.of("facturation-api"), UC, Set.of()))
                .containsExactly("Tâche 2 : la base doit être un module de <modules> cité tel quel.");
    }

    @Test
    void laFormeSeuleVautPourLesCorrectionsDeLUtilisateur() {
        TaskProposal p = of(new ProposedTask("Ajoutée", "R", Basis.NONE, List.of(), true));
        assertThat(p.shapeProblems()).isEmpty();
    }
}
