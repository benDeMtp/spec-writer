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
}
