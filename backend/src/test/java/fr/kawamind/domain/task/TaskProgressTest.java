package fr.kawamind.domain.task;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.task.TaskProgress.Next;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** T037, T052 (015, data-model.md, R4, R7) : choix déterministe de la prochaine tâche, avancement et statut. */
class TaskProgressTest {

    static final UUID SESSION = new UUID(0, 1);
    static final Instant T = Instant.parse("2026-11-17T10:00:00Z");
    static final String ME = "100-1";
    static final String OTHER = "200-2";
    static final String CLOSED = "300-3";
    static final Predicate<String> ALIVE = c -> !CLOSED.equals(c);

    final List<SpecTask> tasks = new ArrayList<>();

    SpecTask add(TaskStatus status, String holder, SpecTask... deps) {
        int n = tasks.size();
        SpecTask t = SpecTask.proposed(new UUID(1, n), SESSION, n, "T" + n, "R", Basis.useCase("UC-01"),
                TaskOrigin.PROPOSED, java.util.Arrays.stream(deps).map(SpecTask::id).toList()).created("FACT-" + (10 + n));
        if (status == TaskStatus.IN_PROGRESS) {
            t = holder == null ? t.read(TaskStatus.IN_PROGRESS, "En cours", T)
                    : t.started(new Holder(holder, "agent-" + holder, T), false);
        } else if (status != TaskStatus.TODO) {
            t = t.read(status, null, T);
        }
        tasks.add(t);
        return t;
    }

    Next next(String connection) {
        return TaskProgress.next(tasks, connection, ALIVE);
    }

    @Test
    void laTacheEnCoursDeLaConnexionPasseDAbord() {
        add(TaskStatus.TODO, null);
        SpecTask held = add(TaskStatus.IN_PROGRESS, OTHER);
        SpecTask mine = add(TaskStatus.IN_PROGRESS, ME);
        assertThat(next(ME)).isEqualTo(new Next.Task(mine, true));
        assertThat(next(OTHER)).isEqualTo(new Next.Task(held, true));
    }

    @Test
    void puisLaPremiereTenueParUneConnexionFermee() {
        add(TaskStatus.TODO, null);
        SpecTask orphan = add(TaskStatus.IN_PROGRESS, CLOSED);
        assertThat(next(ME)).isEqualTo(new Next.Task(orphan, true));
    }

    @Test
    void puisLaPremiereAFaireDontLesPrealablesSontTermines() {
        SpecTask a = add(TaskStatus.IN_PROGRESS, OTHER);
        add(TaskStatus.TODO, null, a);
        SpecTask c = add(TaskStatus.TODO, null);
        assertThat(next(ME)).isEqualTo(new Next.Task(c, false));
        assertThat(next(ME)).as("deux appels, même réponse").isEqualTo(next(ME));
    }

    @Test
    void unPrealableIntrouvableNeBloquePas() {
        SpecTask gone = add(TaskStatus.MISSING, null);
        SpecTask b = add(TaskStatus.TODO, null, gone);
        assertThat(next(ME)).isEqualTo(new Next.Task(b, false));
    }

    @Test
    void uneTacheEnCoursDansJiraSansAgentNEstPasProposee() {
        add(TaskStatus.IN_PROGRESS, null);
        assertThat(next(ME)).isEqualTo(new Next.AllHeld(List.of()));
    }

    @Test
    void plusRienOuToutEstTenu() {
        add(TaskStatus.DONE, null);
        add(TaskStatus.MISSING, null);
        assertThat(next(ME)).isEqualTo(new Next.NoneLeft());
        SpecTask held = add(TaskStatus.IN_PROGRESS, OTHER);
        add(TaskStatus.TODO, null, held);
        assertThat(next(ME)).isEqualTo(new Next.AllHeld(List.of("agent-" + OTHER)));
    }

    @Test
    void lesTachesNonCreeesNeSontPasSuivies() {
        tasks.add(SpecTask.proposed(new UUID(2, 0), SESSION, 0, "Pas créée", "R", Basis.NONE, TaskOrigin.MANUAL,
                List.of()));
        assertThat(next(ME)).isEqualTo(new Next.NoneLeft());
        assertThat(TaskProgress.total(tasks)).isZero();
        assertThat(TaskProgress.status(tasks)).isEmpty();
    }

    @Test
    void avancementHorsIntrouvables() {
        add(TaskStatus.DONE, null);
        add(TaskStatus.DONE, null);
        add(TaskStatus.TODO, null);
        add(TaskStatus.MISSING, null);
        assertThat(TaskProgress.done(tasks)).isEqualTo(2);
        assertThat(TaskProgress.total(tasks)).isEqualTo(3);
    }

    @Test
    void leStatutSuitLesTaches() {
        add(TaskStatus.TODO, null);
        add(TaskStatus.MISSING, null);
        assertThat(TaskProgress.status(tasks)).contains(ImplementationStatus.READY);
        tasks.set(0, tasks.get(0).read(TaskStatus.DONE, null, T));
        assertThat(TaskProgress.status(tasks)).contains(ImplementationStatus.IMPLEMENTED);
        add(TaskStatus.TODO, null);
        assertThat(TaskProgress.status(tasks)).contains(ImplementationStatus.IN_PROGRESS);
        tasks.set(0, tasks.get(0).read(TaskStatus.TODO, null, T));
        tasks.set(2, tasks.get(2).read(TaskStatus.IN_PROGRESS, null, T));
        assertThat(TaskProgress.status(tasks)).contains(ImplementationStatus.IN_PROGRESS);
        assertThat(Set.of(TaskProgress.status(List.of()))).containsExactly(java.util.Optional.empty());
    }
}
