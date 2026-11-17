package fr.kawamind.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskOrigin;
import fr.kawamind.support.TaskWorld;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T020 (015, contracts/rest-api.md) : ajout manuel, renommage, ordre, retrait d'une tâche non créée. */
class EditTasksTest {

    final TaskWorld w = new TaskWorld();
    final ReadTasks read = new ReadTasks(w.desk, w.connections);
    final EditTasks edit = new EditTasks(w.desk, read);
    Session session;
    SpecTask a;
    SpecTask b;

    @BeforeEach
    void setUp() {
        session = w.linkedSession();
        a = SpecTask.proposed(w.ids.next(), session.id(), 0, "A", "R", Basis.useCase("UC-01"), TaskOrigin.PROPOSED,
                List.of());
        b = SpecTask.proposed(w.ids.next(), session.id(), 1, "B", "R", Basis.useCase("UC-01"), TaskOrigin.PROPOSED,
                List.of(a.id()));
        w.tracker.addSubtask("FACT-120", "FACT-124", "A", "new");
        a = a.created("FACT-124");
        w.tasks.replaceAll(session.id(), List.of(a, b));
    }

    static void refused(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void unAjoutManuelEstCreeAussitot() {
        TasksView view = edit.add(session.id(), "Documenter", "La page existe", List.of(a.id()));
        SpecTask added = view.tasks().get(2);
        assertThat(added.origin()).isEqualTo(TaskOrigin.MANUAL);
        assertThat(added.isCreated()).isTrue();
        assertThat(view.tasks().get(1).isCreated()).as("les tâches restantes sont créées aussi").isTrue();
        assertThat(added.dependsOn()).containsExactly(a.id());
    }

    @Test
    void renommerUneTacheCreeeRenommeLaSousTache() {
        edit.edit(session.id(), a.id(), "A renommée", null, null);
        assertThat(w.tracker.calls).contains("summary FACT-124 A renommée");
        edit.edit(session.id(), b.id(), "B renommée", null, null);
        assertThat(w.tracker.calls).noneMatch(c -> c.startsWith("summary") && c.contains("B renommée"));
        assertThat(w.tasks.tasks(session.id())).extracting(SpecTask::title).containsExactly("A renommée", "B renommée");
    }

    @Test
    void unPrealableDoitPreceder() {
        refused(() -> edit.edit(session.id(), b.id(), null, 0, null), ErrorCode.VALIDATION_FAILED);
        edit.edit(session.id(), b.id(), null, 0, List.of());
        assertThat(w.tasks.tasks(session.id())).extracting(SpecTask::title).containsExactly("B", "A");
    }

    @Test
    void seuleUneTacheNonCreeePeutEtreRetiree() {
        refused(() -> edit.delete(session.id(), a.id()), ErrorCode.CANNOT_DELETE_CREATED);
        TasksView view = edit.delete(session.id(), b.id());
        assertThat(view.tasks()).extracting(SpecTask::title).containsExactly("A");
        assertThat(w.tracker.calls).noneMatch(c -> c.contains("delete"));
    }
}
