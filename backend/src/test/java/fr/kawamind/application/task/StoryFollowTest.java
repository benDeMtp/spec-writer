package fr.kawamind.application.task;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.jira.IssueTrackerException.Kind;
import fr.kawamind.domain.jira.Transition;
import fr.kawamind.domain.jira.TrackedIssue;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.TaskWorkflow;
import fr.kawamind.support.TaskWorld;
import java.time.Instant;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T025 (023, US4, research R7) : la story de la feature suit ses tâches dans Jira. */
class StoryFollowTest {

    static final TaskWorkflow AURORA = TaskWorkflow.of("Réalisée", List.of("En cours de test"));

    final TaskWorld w = new TaskWorld();
    final StoryFollow follow = new StoryFollow(w.desk, new TaskJira(w.tracker));
    final java.util.List<LogRecord> logs = new java.util.ArrayList<>();
    final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord r) {
            logs.add(r);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    Session session;

    @BeforeEach
    void setUp() {
        session = w.linkedSession();
        Logger.getLogger(StoryFollow.class.getName()).addHandler(capture);
    }

    @AfterEach
    void tearDown() {
        Logger.getLogger(StoryFollow.class.getName()).removeHandler(capture);
    }

    void story(String status, String category) {
        w.tracker.tracked.put("FACT-120", new TrackedIssue("FACT-120", "Story", "", status, category, "Story",
                Instant.parse("2026-11-26T09:00:00Z"), List.of()));
        w.tracker.workflows.put("FACT-120", List.of(new Transition("31", "Réaliser directement", "indeterminate", "Réalisée"),
                new Transition("21", "Démarrer", "indeterminate", "En Cours"),
                new Transition("61", "Fermer", "done", "Fermée"), new Transition("81", "Annuler", "done", "Annulée")));
    }

    @Test
    void auDemarrageUneStoryAFairePasseEnCoursSansSauterAUnStatutFini() {
        w.workflows.save(session.projectId(), AURORA, w.clock.now());
        story("Prête", "new");
        StoryMove moved = follow.started(session.id());
        assertThat(moved).isEqualTo(new StoryMove("FACT-120", StoryMove.Outcome.MOVED, "En Cours", null));
        assertThat(w.tracker.calls).contains("transition FACT-120 21").doesNotContain("transition FACT-120 31");
        // déjà en cours : rien
        assertThat(follow.started(session.id())).isNull();
    }

    @Test
    void aLaFinLaStoryPasseAuStatutDArrivee() {
        w.workflows.save(session.projectId(), AURORA, w.clock.now());
        story("En Cours", "indeterminate");
        assertThat(follow.allDone(session.id()))
                .isEqualTo(new StoryMove("FACT-120", StoryMove.Outcome.MOVED, "Réalisée", null));
        assertThat(w.tracker.tracked.get("FACT-120").status()).isEqualTo("Réalisée");
        assertThat(follow.allDone(session.id()).status()).isEqualTo(StoryMove.Outcome.ALREADY);
    }

    @Test
    void sansReglageLaStoryNEstPasDeplaceeALaFin() {
        story("En Cours", "indeterminate");
        assertThat(follow.allDone(session.id()))
                .isEqualTo(new StoryMove("FACT-120", StoryMove.Outcome.SKIPPED, null, null));
        assertThat(w.tracker.calls).noneMatch(c -> c.startsWith("transition FACT-120"));
    }

    @Test
    void unEchecEstSignaleSansExceptionNiRejeu() {
        w.workflows.save(session.projectId(), AURORA, w.clock.now());
        story("En Cours", "indeterminate");
        w.tracker.workflows.put("FACT-120", List.of(new Transition("61", "Fermer", "done", "Fermée")));
        StoryMove failed = follow.allDone(session.id());
        assertThat(failed.status()).isEqualTo(StoryMove.Outcome.FAILED);
        assertThat(failed.to()).isEqualTo("Réalisée");
        assertThat(failed.message()).contains("FACT-120").contains("Faites-la dans Jira");
        assertThat(logs).anySatisfy(r -> {
            assertThat(r.getLevel()).isEqualTo(Level.WARNING);
            assertThat(r.getMessage()).contains("FACT-120");
        });

        w.tracker.failure = Kind.UNREACHABLE;
        StoryMove offline = follow.allDone(session.id());
        assertThat(offline.status()).isEqualTo(StoryMove.Outcome.FAILED);
        assertThat(offline.message()).contains("injoignable");
    }

    @Test
    void sansTicketLieRien() {
        Session alone = w.generatedSession();
        assertThat(follow.started(alone.id())).isNull();
        assertThat(follow.allDone(alone.id())).isNull();
    }
}
