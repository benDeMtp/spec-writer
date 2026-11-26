package fr.kawamind.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.task.TaskJira.Result;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueTrackerException.Kind;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.Transition;
import fr.kawamind.domain.task.StatusCategory;
import fr.kawamind.domain.task.Target;
import fr.kawamind.domain.task.TaskWorkflow;
import fr.kawamind.support.FakeIssueTracker;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T014 (015, research R3, R5) : transition choisie par catégorie de statut, refus et panne distingués. */
class TaskJiraTest {

    static final JiraEndpoint JIRA = new JiraEndpoint("https://jira.example", "pat", null);

    FakeIssueTracker tracker;
    TaskJira jira;

    @BeforeEach
    void setUp() {
        tracker = new FakeIssueTracker();
        tracker.addSubtask("FACT-120", "FACT-124", "Exposer l'endpoint", "new");
        jira = new TaskJira(tracker);
    }

    @Test
    void laPremiereTransitionVersLaCategorieEstPrise() {
        tracker.workflows.put("FACT-124", List.of(new Transition("5", "Bloquer", "indeterminate"),
                new Transition("21", "Démarrer", "indeterminate"), new Transition("31", "Clore", "done")));
        assertThat(jira.moveTo(JIRA, "FACT-124", StatusCategory.IN_PROGRESS)).isEqualTo(Result.SENT);
        assertThat(tracker.calls).contains("transition FACT-124 5");
    }

    /** Une annulation mène aussi à la catégorie « terminé » : elle n'est jamais prise pour terminer. */
    @Test
    void terminerNePrendJamaisUneAnnulation() {
        tracker.workflows.put("FACT-124", List.of(new Transition("41", "Annuler", "done", "Annulé"),
                new Transition("51", "Rejeter", "done", "Won't Do"), new Transition("31", "Clore", "done", "Terminé")));
        assertThat(jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE)).isEqualTo(Result.SENT);
        assertThat(tracker.calls).contains("transition FACT-124 31").doesNotContain("transition FACT-124 41");
    }

    @Test
    void uneTransitionNeutreVersUnStatutAnnuleEstEcartee() {
        tracker.workflows.put("FACT-124", List.of(new Transition("41", "Clôturer", "done", "Cancelled"),
                new Transition("61", "Fermer", "done", "Closed")));
        jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE);
        assertThat(tracker.calls).contains("transition FACT-124 61");
    }

    @Test
    void unStatutTermineEstPrefereAUnStatutQuelconque() {
        tracker.workflows.put("FACT-124", List.of(new Transition("71", "Archiver", "done", "Archivé"),
                new Transition("31", "Livrer", "done", "Resolved")));
        jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE);
        assertThat(tracker.calls).contains("transition FACT-124 31");
    }

    @Test
    void seulementDesAnnulationsLeRefusPlutotQuAnnuler() {
        tracker.workflows.put("FACT-124", List.of(new Transition("41", "Annuler", "done", "Annulé"),
                new Transition("81", "Marquer en doublon", "done", "Duplicate")));
        assertThatThrownBy(() -> jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.NO_TRANSITION);
                    assertThat(e.userMessage()).contains("annulation");
                });
        assertThat(tracker.calls).noneMatch(c -> c.startsWith("transition FACT-124"));
    }

    @Test
    void dejaDansLaCategorieAucunAppel() {
        assertThat(jira.moveTo(JIRA, "FACT-124", StatusCategory.NEW)).isEqualTo(Result.ALREADY);
        assertThat(tracker.calls).noneMatch(c -> c.startsWith("transition"));
    }

    @Test
    void sansTransitionDirecteLeRefusNommeLeStatutActuel() {
        tracker.workflows.put("FACT-124", List.of(new Transition("21", "Démarrer", "indeterminate")));
        assertThatThrownBy(() -> jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.NO_TRANSITION);
                    assertThat(e.userMessage()).contains("« À faire »").contains("« Terminé »");
                });
    }

    @Test
    void jiraInjoignableNEstPasUnRefus() {
        tracker.failingCall = "transition";
        assertThat(jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE)).isEqualTo(Result.UNREACHABLE);
        tracker.failingCall = null;
        tracker.failure = Kind.UNREACHABLE;
        assertThat(jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE)).isEqualTo(Result.UNREACHABLE);
        assertThat(jira.comment(JIRA, "FACT-124", "Fait")).isEqualTo(Result.UNREACHABLE);
    }

    @Test
    void unRefusDeJiraEstUneErreur() {
        tracker.transitionFailure = Kind.REJECTED;
        assertThatThrownBy(() -> jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.JIRA_REFUSED));
        assertThatThrownBy(() -> jira.comment(JIRA, "FACT-124", "Fait")).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.JIRA_REFUSED));
    }

    @Test
    void leCommentaireEstEnvoye() {
        assertThat(jira.comment(JIRA, "FACT-124", "Fait")).isEqualTo(Result.SENT);
        assertThat(tracker.comments).containsExactly("FACT-124 : Fait");
    }

    // 023 (US1, research R4) : un statut nommé du workflow, par une transition directe

    static final TaskWorkflow AURORA = TaskWorkflow.of("Réalisée", List.of("En cours de test"));
    static final Target REALISEE = AURORA.completion();

    void aurora(String status) {
        tracker.moveTo("FACT-124", "indeterminate", status);
        tracker.workflows.put("FACT-124", List.of(new Transition("41", "Annuler la réalisation", "indeterminate", "Réalisée"),
                new Transition("61", "Fermer", "done", "Fermée"), new Transition("51", "Réaliser", "indeterminate", "Realisee"),
                new Transition("81", "Annuler", "done", "Annulée")));
    }

    @Test
    void laTransitionVersLeStatutDArriveeEstPrisePasLaFermeture() {
        aurora("En Cours");
        assertThat(jira.moveTo(JIRA, "FACT-124", REALISEE)).isEqualTo(Result.SENT);
        assertThat(tracker.calls).contains("transition FACT-124 51").doesNotContain("transition FACT-124 61")
                .doesNotContain("transition FACT-124 41");
        assertThat(tracker.tracked.get("FACT-124").status()).isEqualTo("Realisee");
    }

    @Test
    void uneSousTacheDejaFinieNeRecoitAucuneTransition() {
        aurora("En cours de test");
        assertThat(jira.moveTo(JIRA, "FACT-124", REALISEE)).isEqualTo(Result.ALREADY);
        aurora("Réalisée");
        assertThat(jira.moveTo(JIRA, "FACT-124", REALISEE)).isEqualTo(Result.ALREADY);
        assertThat(tracker.calls).noneMatch(c -> c.startsWith("transition FACT-124 "));
    }

    @Test
    void sansTransitionDirecteRienNeChange() {
        tracker.moveTo("FACT-124", "indeterminate", "En Cours");
        tracker.workflows.put("FACT-124", List.of(new Transition("61", "Fermer", "done", "Fermée")));
        assertThatThrownBy(() -> jira.moveTo(JIRA, "FACT-124", REALISEE)).isInstanceOfSatisfying(DomainException.class,
                e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.NO_TRANSITION);
                    assertThat(e.userMessage()).isEqualTo("Le workflow Jira de FACT-124 n'offre aucune transition directe"
                            + " de « En Cours » vers « Réalisée ». Faites-la dans Jira.");
                });
        assertThat(tracker.calls).noneMatch(c -> c.startsWith("transition FACT-124 "));
    }

    @Test
    void uneAnnulationVersLeStatutDArriveeNEstPasPrise() {
        tracker.moveTo("FACT-124", "indeterminate", "En Cours");
        tracker.workflows.put("FACT-124", List.of(new Transition("41", "Annuler la réalisation", "indeterminate",
                "Réalisée")));
        assertThatThrownBy(() -> jira.moveTo(JIRA, "FACT-124", REALISEE)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_TRANSITION));
    }

    @Test
    void uneCibleCategorieGardeLeComportementDeLa015() {
        assertThat(jira.moveTo(JIRA, "FACT-124", new Target.Category(StatusCategory.IN_PROGRESS))).isEqualTo(Result.SENT);
        assertThat(tracker.tracked.get("FACT-124").statusCategory()).isEqualTo("indeterminate");
    }

    // 023 (US5, research R9) : le propriétaire du jeton est affecté avant chaque transition

    @Test
    void leTicketEstAffecteAuProprietaireDuJetonAvantLaTransition() {
        aurora("En Cours");
        tracker.assignees.put("FACT-124", "bob");
        TaskJira.Move move = jira.move(JIRA, "FACT-124", REALISEE);
        assertThat(move).isEqualTo(new TaskJira.Move(Result.SENT, null));
        assertThat(tracker.calls).containsSubsequence("myself", "assign FACT-124 ada", "transition FACT-124 51");
        assertThat(tracker.assignees).containsEntry("FACT-124", "ada");
    }

    @Test
    void unTicketDejaAffecteAuProprietaireNEstPasReaffecte() {
        tracker.assignees.put("FACT-124", "ada");
        assertThat(jira.moveTo(JIRA, "FACT-124", StatusCategory.IN_PROGRESS)).isEqualTo(Result.SENT);
        assertThat(tracker.calls).noneMatch(c -> c.startsWith("assign"));
    }

    @Test
    void sansTransitionEnvoyeeAucuneAffectation() {
        aurora("Réalisée");
        assertThat(jira.moveTo(JIRA, "FACT-124", REALISEE)).isEqualTo(Result.ALREADY);
        tracker.moveTo("FACT-124", "indeterminate", "En Cours");
        tracker.workflows.put("FACT-124", List.of(new Transition("61", "Fermer", "done", "Fermée")));
        assertThatThrownBy(() -> jira.moveTo(JIRA, "FACT-124", REALISEE)).isInstanceOf(DomainException.class);
        assertThat(tracker.calls).noneMatch(c -> c.startsWith("assign"));
    }

    @Test
    void laRemiseAFaireNAffectePas() {
        tracker.moveTo("FACT-124", "indeterminate", "En cours");
        assertThat(jira.moveTo(JIRA, "FACT-124", StatusCategory.NEW)).isEqualTo(Result.SENT);
        assertThat(tracker.calls).noneMatch(c -> c.startsWith("assign"));
    }

    @Test
    void leDemarrageAffecteAussi() {
        TaskJira.Started started = jira.start(JIRA, "FACT-124", TaskWorkflow.NONE);
        assertThat(started.result()).isEqualTo(Result.SENT);
        assertThat(started.assignProblem()).isNull();
        assertThat(tracker.calls).containsSubsequence("assign FACT-124 ada", "transition FACT-124 21");
    }

    @Test
    void uneAffectationRefuseeNEmpechePasLaTransition() {
        tracker.assignFailure = Kind.REJECTED;
        TaskJira.Move move = jira.move(JIRA, "FACT-124", new Target.Category(StatusCategory.IN_PROGRESS));
        assertThat(move.result()).isEqualTo(Result.SENT);
        assertThat(move.assignProblem()).isEqualTo("FACT-124 n'a pas pu être affecté à ada : "
                + "L'utilisateur ne peut pas être affecté.");
        assertThat(tracker.calls).contains("transition FACT-124 21");
        tracker.assignFailure = Kind.UNAUTHORIZED;
        tracker.moveTo("FACT-124", "new", "À faire");
        assertThat(jira.move(JIRA, "FACT-124", new Target.Category(StatusCategory.IN_PROGRESS)).assignProblem())
                .contains("FACT-124 n'a pas pu être affecté à ada");
    }

    @Test
    void jiraInjoignablePendantLAffectationEstInjoignable() {
        tracker.assignFailure = Kind.UNREACHABLE;
        assertThat(jira.moveTo(JIRA, "FACT-124", StatusCategory.IN_PROGRESS)).isEqualTo(Result.UNREACHABLE);
        assertThat(tracker.calls).noneMatch(c -> c.startsWith("transition FACT-124"));
    }

    @Test
    void leProprietaireEstLuUneFoisParJeton() {
        jira.moveTo(JIRA, "FACT-124", StatusCategory.IN_PROGRESS);
        tracker.assignees.remove("FACT-124");
        jira.moveTo(JIRA, "FACT-124", StatusCategory.DONE);
        assertThat(tracker.calls.stream().filter("myself"::equals)).hasSize(1);
        // un autre jeton : relu
        tracker.moveTo("FACT-124", "new", "À faire");
        jira.moveTo(new JiraEndpoint("https://jira.example", "autre", null), "FACT-124", StatusCategory.IN_PROGRESS);
        assertThat(tracker.calls.stream().filter("myself"::equals)).hasSize(2);
    }
}
