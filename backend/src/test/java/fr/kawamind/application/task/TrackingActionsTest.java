package fr.kawamind.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.agent.AgentJournal;
import fr.kawamind.application.session.ChangeStatus;
import fr.kawamind.domain.agent.AgentAccess;
import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueTrackerException.Kind;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskOrigin;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.domain.task.TaskTracking;
import fr.kawamind.support.InMemoryAgentAccess;
import fr.kawamind.support.TaskWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T053 à T055 (015, US3, R6, R7, FR-020a, FR-022) : relecture, statut calculé, libération, suspension. */
class TrackingActionsTest {

    final TaskWorld w = new TaskWorld();
    final InMemoryAgentAccess access = new InMemoryAgentAccess();
    final AgentJournal journal = new AgentJournal(access, w.clock, w.ids);
    final TaskJira jira = new TaskJira(w.tracker);
    final TaskSync sync = new TaskSync(w.desk, jira);
    final ReadTasks read = new ReadTasks(w.desk, w.connections, sync);
    final TrackingActions actions = new TrackingActions(w.desk, sync, jira, read, journal);
    final AgentTasks agent = new AgentTasks(w.desk, sync, jira, w.connections, journal);
    final ChangeStatus user = new ChangeStatus(w.sessions, w.clock, w.tasks);
    Session session;

    @BeforeEach
    void setUp() {
        session = w.linkedSession();
        user.run(session.id(), ImplementationStatus.READY, false);
        List<SpecTask> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String key = "FACT-" + (124 + i);
            w.tracker.addSubtask("FACT-120", key, "T" + i, i < 3 ? "done" : "new");
            tasks.add(SpecTask.proposed(w.ids.next(), session.id(), i, "T" + i, "R", Basis.useCase("UC-01"),
                    TaskOrigin.PROPOSED, List.of()).created(key));
        }
        w.tasks.replaceAll(session.id(), tasks);
    }

    ImplementationStatus status() {
        return w.sessions.implementation(session.id()).orElseThrow().status();
    }

    @Test
    void lOuvertureRelitJiraEtLeStatutSuit() {
        TasksView v = read.run(session.id());
        assertThat(v.done()).isEqualTo(3);
        assertThat(v.total()).isEqualTo(5);
        assertThat(status()).isEqualTo(ImplementationStatus.IN_PROGRESS);
        assertThat(v.tasks().get(0).jiraStatusName()).isEqualTo("Terminé");
        w.tracker.moveTo("FACT-127", "done");
        w.tracker.moveTo("FACT-128", "done");
        assertThat(read.run(session.id()).done()).isEqualTo(5);
        assertThat(status()).isEqualTo(ImplementationStatus.IMPLEMENTED);
        w.tracker.moveTo("FACT-128", "indeterminate");
        read.run(session.id());
        assertThat(status()).as("rouverte dans Jira").isEqualTo(ImplementationStatus.IN_PROGRESS);
    }

    @Test
    void uneSousTacheSupprimeeEstIntrouvableEtJiraInjoignableGardeLesStatuts() {
        w.tracker.tracked.remove("FACT-128");
        TasksView v = read.run(session.id());
        assertThat(v.tasks().get(4).status()).isEqualTo(TaskStatus.MISSING);
        assertThat(v.total()).isEqualTo(4);
        w.tracker.failure = Kind.UNREACHABLE;
        TasksView offline = read.run(session.id());
        assertThat(offline.readProblem()).isTrue();
        assertThat(offline.done()).isEqualTo(3);
        assertThat(offline.lastReadAt()).isEqualTo(v.lastReadAt());
    }

    @Test
    void libererRemetAFaireDansJiraEtAuJournal() {
        agent.start("claude-code", "100-1", session.id(), "FACT-127");
        UUIDHolder id = new UUIDHolder(w.tasks.tasks(session.id()).get(3).id());
        TasksView v = actions.release(session.id(), id.value);
        assertThat(v.tasks().get(3).status()).isEqualTo(TaskStatus.TODO);
        assertThat(v.tasks().get(3).holder()).isNull();
        assertThat(w.tracker.tracked.get("FACT-127").statusCategory()).isEqualTo("new");
        assertThat(access.entries).filteredOn(a -> a.operation() == AgentOperation.RELEASE_TASK)
                .extracting(AgentAccess::agentName).containsExactly("claude-code");
        assertThatThrownBy(() -> actions.release(session.id(), id.value)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.TASK_NOT_READY));
    }

    record UUIDHolder(java.util.UUID value) {
    }

    @Test
    void synchroniserEnvoieLesTransitionsEnAttente() {
        agent.start("claude-code", "100-1", session.id(), "FACT-127");
        w.tracker.failure = Kind.UNREACHABLE;
        agent.complete("claude-code", "100-1", session.id(), "FACT-127", null);
        assertThat(read.current(session.id()).pendingSync()).isEqualTo(1);
        w.tracker.failure = null;
        TasksView v = actions.sync(session.id());
        assertThat(v.pendingSync()).isZero();
        assertThat(w.tracker.tracked.get("FACT-127").statusCategory()).isEqualTo("done");
    }

    @Test
    void leStatutSuitLesTachesEtRepasserEnPreteSuspendLeSuivi() {
        read.run(session.id());
        assertThatThrownBy(() -> user.run(session.id(), ImplementationStatus.IMPLEMENTED, false))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.STATUS_FOLLOWS_TASKS));
        user.run(session.id(), ImplementationStatus.READY, false);
        assertThat(w.tasks.tracking(session.id())).isEqualTo(TaskTracking.SUSPENDED);
        read.run(session.id());
        assertThat(status()).as("suspendu : le statut ne suit plus").isEqualTo(ImplementationStatus.READY);
        actions.resume(session.id());
        assertThat(w.tasks.tracking(session.id())).isEqualTo(TaskTracking.ACTIVE);
        assertThat(status()).isEqualTo(ImplementationStatus.IN_PROGRESS);
    }

    @Test
    void passerDeBrouillonAPreteNeSuspendPas() {
        user.run(session.id(), ImplementationStatus.DRAFT, false);
        w.tasks.setTracking(session.id(), TaskTracking.ACTIVE);
        user.run(session.id(), ImplementationStatus.READY, false);
        assertThat(w.tasks.tracking(session.id())).isEqualTo(TaskTracking.ACTIVE);
    }

    @Test
    void reprendreDepuisUnBrouillonRecalculeLeStatut() {
        user.run(session.id(), ImplementationStatus.DRAFT, false);
        assertThat(w.tasks.tracking(session.id())).isEqualTo(TaskTracking.SUSPENDED);
        actions.resume(session.id());
        assertThat(status()).isEqualTo(ImplementationStatus.IN_PROGRESS);
    }
}
