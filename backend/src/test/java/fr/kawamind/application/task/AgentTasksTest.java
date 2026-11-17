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
import fr.kawamind.domain.jira.Transition;
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

/** T038 à T040 (015, US2, contracts/mcp-tools.md) : prochaine tâche, prise et fin par un agent. */
class AgentTasksTest {

    static final String ME = "100-1";
    static final String OTHER = "200-2";

    final TaskWorld w = new TaskWorld();
    final InMemoryAgentAccess access = new InMemoryAgentAccess();
    final AgentJournal journal = new AgentJournal(access, w.clock, w.ids);
    final TaskJira jira = new TaskJira(w.tracker);
    final TaskSync sync = new TaskSync(w.desk, jira);
    final AgentTasks agent = new AgentTasks(w.desk, sync, jira, w.connections, journal);
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
            tasks.add(SpecTask.proposed(w.ids.next(), session.id(), i, "T" + i, "R" + i, Basis.useCase("UC-01"),
                    TaskOrigin.PROPOSED, i == 2 ? List.of(tasks.get(0).id()) : List.of()).created(key));
        }
        w.tasks.replaceAll(session.id(), tasks);
    }

    static void refused(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    ImplementationStatus status() {
        return w.sessions.implementation(session.id()).orElseThrow().status();
    }

    @Test
    void troisFoisProchainePrendreTerminerPuisPlusRien() {
        for (int i = 0; i < 3; i++) {
            AgentTasks.NextAnswer next = agent.next("claude-code", ME, session.id());
            assertThat(next.outcome()).isEqualTo("TASK");
            assertThat(next.resume()).isFalse();
            assertThat(agent.next("claude-code", ME, session.id())).as("déterministe").isEqualTo(next);
            String key = next.task().key();
            AgentTasks.TaskAnswer started = agent.start("claude-code", ME, session.id(), key);
            assertThat(started.task().status()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(started.task().heldByYou()).isTrue();
            assertThat(started.jiraSync()).isEqualTo("OK");
            assertThat(w.tracker.tracked.get(key).statusCategory()).isEqualTo("indeterminate");
            assertThat(status()).isEqualTo(i == 2 ? ImplementationStatus.IN_PROGRESS : ImplementationStatus.IN_PROGRESS);
            AgentTasks.TaskAnswer done = agent.complete("claude-code", ME, session.id(), key, i == 0 ? "Tests verts" : null);
            assertThat(done.task().status()).isEqualTo(TaskStatus.DONE);
            assertThat(w.tracker.tracked.get(key).statusCategory()).isEqualTo("done");
        }
        assertThat(agent.next("claude-code", ME, session.id()).outcome()).isEqualTo("NONE_LEFT");
        assertThat(status()).isEqualTo(ImplementationStatus.IMPLEMENTED);
        assertThat(w.tracker.comments).containsExactly("FACT-124 : wiki:Compte rendu de claude-code : Tests verts");
        assertThat(access.entries).extracting(AgentAccess::operation).filteredOn(o -> o != AgentOperation.NEXT_TASK)
                .containsExactly(AgentOperation.START_TASK, AgentOperation.COMPLETE_TASK, AgentOperation.START_TASK,
                        AgentOperation.COMPLETE_TASK, AgentOperation.START_TASK, AgentOperation.COMPLETE_TASK);
        assertThat(access.entries).filteredOn(a -> a.operation() == AgentOperation.START_TASK)
                .extracting(AgentAccess::taskKey).containsExactly("FACT-124", "FACT-125", "FACT-126");
    }

    @Test
    void uneTacheTenueParUneAutreConnexionOuverteEstRefusee() {
        agent.start("claude-code", OTHER, session.id(), "FACT-124");
        assertThatThrownBy(() -> agent.start("codex", ME, session.id(), "FACT-124")).isInstanceOfSatisfying(
                DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.TASK_HELD);
                    assertThat(e.userMessage()).contains("claude-code");
                });
        AgentTasks.NextAnswer next = agent.next("codex", ME, session.id());
        assertThat(next.task().key()).isEqualTo("FACT-125");
        refused(() -> agent.complete("codex", ME, session.id(), "FACT-124", null), ErrorCode.TASK_NOT_READY);
    }

    @Test
    void uneConnexionFermeeLaisseSaTacheALaConversationSuivante() {
        agent.start("claude-code", OTHER, session.id(), "FACT-124");
        w.closed.add(OTHER);
        AgentTasks.NextAnswer next = agent.next("claude-code", ME, session.id());
        assertThat(next.task().key()).isEqualTo("FACT-124");
        assertThat(next.resume()).isTrue();
        AgentTasks.TaskAnswer resumed = agent.start("claude-code", ME, session.id(), "FACT-124");
        assertThat(resumed.task().heldByYou()).isTrue();
        assertThat(w.tracker.calls).filteredOn(c -> c.startsWith("transition FACT-124")).hasSize(1);
    }

    @Test
    void lesPrealablesEtLesTachesIntrouvablesBloquent() {
        refused(() -> agent.start("claude-code", ME, session.id(), "FACT-126"), ErrorCode.TASK_NOT_READY);
        refused(() -> agent.start("claude-code", ME, session.id(), "FACT-999"), ErrorCode.NOT_FOUND);
        w.tracker.children.get("FACT-120").remove("FACT-125");
        w.tracker.tracked.remove("FACT-125");
        refused(() -> agent.start("claude-code", ME, session.id(), "FACT-125"), ErrorCode.TASK_NOT_READY);
        assertThat(w.tasks.tasks(session.id()).get(1).status()).isEqualTo(TaskStatus.MISSING);
    }

    @Test
    void jiraInjoignableLaTransitionAttendEtLAgentEstPrevenu() {
        agent.start("claude-code", ME, session.id(), "FACT-124");
        w.tracker.failure = Kind.UNREACHABLE;
        AgentTasks.TaskAnswer done = agent.complete("claude-code", ME, session.id(), "FACT-124", "Fait");
        assertThat(done.jiraSync()).isEqualTo("PENDING");
        assertThat(done.message()).contains("Jira est injoignable");
        SpecTask stored = w.tasks.tasks(session.id()).get(0);
        assertThat(stored.status()).isEqualTo(TaskStatus.DONE);
        assertThat(stored.pendingTarget()).isEqualTo(TaskStatus.DONE);
        assertThat(stored.pendingComment()).isTrue();

        w.tracker.failure = null;
        agent.next("claude-code", ME, session.id());
        assertThat(w.tracker.tracked.get("FACT-124").statusCategory()).isEqualTo("done");
        assertThat(w.tracker.comments).containsExactly("FACT-124 : wiki:Compte rendu de l'agent de code : Fait");
        assertThat(w.tasks.tasks(session.id()).get(0).isPending()).isFalse();
    }

    @Test
    void unWorkflowSansTransitionDirecteRefuseSansRienChanger() {
        w.tracker.workflows.put("FACT-124", List.of(new Transition("31", "Clore", "done")));
        refused(() -> agent.start("claude-code", ME, session.id(), "FACT-124"), ErrorCode.NO_TRANSITION);
        assertThat(w.tasks.tasks(session.id()).get(0).status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void terminerUneTacheDejaTermineeNEstPasUneErreurEtLeCompteRenduEstBorne() {
        agent.start("claude-code", ME, session.id(), "FACT-124");
        refused(() -> agent.complete("claude-code", ME, session.id(), "FACT-124", "x".repeat(2001)),
                ErrorCode.REPORT_TOO_LONG);
        w.tracker.moveTo("FACT-124", "done");
        AgentTasks.TaskAnswer again = agent.complete("claude-code", ME, session.id(), "FACT-124", null);
        assertThat(again.alreadyDone()).isTrue();
    }

    @Test
    void suiviSuspenduSansConnexionOuBrouillon() {
        w.tasks.setTracking(session.id(), TaskTracking.SUSPENDED);
        refused(() -> agent.next("claude-code", ME, session.id()), ErrorCode.TRACKING_SUSPENDED);
        refused(() -> agent.start("claude-code", null, session.id(), "FACT-124"), ErrorCode.BRIDGE_OUTDATED);
        new ChangeStatus(w.sessions, w.clock).run(session.id(), ImplementationStatus.DRAFT, false);
        refused(() -> agent.next("claude-code", ME, session.id()), ErrorCode.NOT_FOUND);
    }

    @Test
    void lesTachesDeLaFeatureSontRelues() {
        agent.start("claude-code", ME, session.id(), "FACT-124");
        w.tracker.moveTo("FACT-125", "done");
        AgentTasks.FeatureTasks feature = agent.forFeature(session.id(), ME);
        assertThat(feature.tasks()).extracting(AgentTasks.TaskInfo::key).containsExactly("FACT-124", "FACT-125", "FACT-126");
        assertThat(feature.tasks().get(0).heldByYou()).isTrue();
        assertThat(feature.tasks().get(2).dependsOn()).containsExactly("FACT-124");
        assertThat(feature.done()).isEqualTo(1);
        assertThat(feature.total()).isEqualTo(3);
        w.tracker.failure = Kind.UNREACHABLE;
        assertThat(agent.forFeature(session.id(), ME).readProblem()).isTrue();
    }
}
