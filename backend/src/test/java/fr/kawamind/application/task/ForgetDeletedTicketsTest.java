package fr.kawamind.application.task;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.session.ChangeStatus;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskOrigin;
import fr.kawamind.domain.task.TaskProgress;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.support.TaskWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T035 (024, US5, research R8) : suites d'un ticket supprimé dans Jira. */
class ForgetDeletedTicketsTest {

    final TaskWorld w = new TaskWorld();
    final TaskJira jira = new TaskJira(w.tracker);
    final TaskSync sync = new TaskSync(w.desk, jira);
    final ForgetDeletedTickets forget = new ForgetDeletedTickets(w.desk, sync, w.lostLinks);
    Session session;
    List<SpecTask> tasks;

    @BeforeEach
    void setUp() {
        session = w.linkedSession();
        new ChangeStatus(w.sessions, w.clock).run(session.id(), ImplementationStatus.READY, false);
        tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String key = "FACT-" + (124 + i);
            w.tracker.addSubtask("FACT-120", key, "T" + i, "new");
            SpecTask t = SpecTask.proposed(w.ids.next(), session.id(), i, "T" + i, "R" + i, Basis.useCase("UC-01"),
                    TaskOrigin.PROPOSED, i == 2 ? List.of(tasks.get(1).id()) : List.of()).created(key);
            tasks.add(i == 0 ? t.read(TaskStatus.DONE, "Terminé", w.clock.now()) : t);
        }
        w.tasks.replaceAll(session.id(), tasks);
    }

    @Test
    void laTacheDUneSousTacheSupprimeeSortDuSuivi() {
        ForgetDeletedTickets.Result r = forget.run(session.projectId(), Set.of("FACT-125", "AUTRE-1"));
        assertThat(r).isEqualTo(new ForgetDeletedTickets.Result(1, 0));
        List<SpecTask> kept = w.tasks.tasks(session.id());
        assertThat(kept).extracting(SpecTask::jiraKey).containsExactly("FACT-124", "FACT-126");
        assertThat(kept).extracting(SpecTask::position).containsExactly(0, 1);
        // le préalable supprimé ne retient plus la tâche suivante
        assertThat(kept.get(1).dependsOn()).isEmpty();
        assertThat(TaskProgress.total(kept)).isEqualTo(2);
        assertThat(TaskProgress.done(kept)).isEqualTo(1);
    }

    @Test
    void laDerniereTacheRestanteFinieRendLaSpecificationImplementee() {
        forget.run(session.projectId(), Set.of("FACT-125", "FACT-126"));
        assertThat(w.sessions.implementation(session.id()).orElseThrow().status())
                .isEqualTo(ImplementationStatus.IMPLEMENTED);
    }

    @Test
    void laSpecificationLieeAUneStorySupprimeePerdSonLienEtLeGarde() {
        ForgetDeletedTickets.Result r = forget.run(session.projectId(), Set.of("FACT-120", "FACT-124", "FACT-125",
                "FACT-126"));
        assertThat(r).isEqualTo(new ForgetDeletedTickets.Result(3, 1));
        assertThat(w.tickets.findBySession(session.id())).isEmpty();
        assertThat(w.lostLinks.find(session.id())).contains("FACT-120");
        assertThat(w.sessions.find(session.id())).isPresent();
        assertThat(w.tasks.tasks(session.id())).isEmpty();
    }

    @Test
    void unAutreProjetNEstPasTouche() {
        forget.run(java.util.UUID.randomUUID(), Set.of("FACT-120", "FACT-125"));
        assertThat(w.tickets.findBySession(session.id())).isPresent();
        assertThat(w.tasks.tasks(session.id())).hasSize(3);
    }
}
