package fr.kawamind.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T005 (015, data-model.md) : bornes d'une tâche et ses changements d'état. */
class SpecTaskTest {

    static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    static SpecTask proposed(String title, String outcome) {
        return SpecTask.proposed(UUID.randomUUID(), SESSION, 0, title, outcome, Basis.useCase("UC-01"),
                TaskOrigin.PROPOSED, List.of());
    }

    static void refused(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void titreEtResultatSontBornes() {
        assertThat(proposed("x".repeat(120), "y".repeat(300)).title()).hasSize(120);
        refused(() -> proposed("", "Résultat"));
        refused(() -> proposed("  ", "Résultat"));
        refused(() -> proposed("x".repeat(121), "Résultat"));
        refused(() -> proposed("Titre", ""));
        refused(() -> proposed("Titre", "y".repeat(301)));
    }

    @Test
    void uneSousTacheDeJiraPeutNePasAvoirDeResultat() {
        SpecTask fromJira = SpecTask.fromJira(UUID.randomUUID(), SESSION, 0, "Maquette", "FACT-121", TaskStatus.DONE,
                "Terminé", NOW);
        assertThat(fromJira.outcome()).isEmpty();
        assertThat(fromJira.basis()).isEqualTo(Basis.NONE);
        assertThat(fromJira.creation()).isEqualTo(TaskCreation.CREATED);
    }

    @Test
    void laBaseNommeUnModuleOuUnCasDUsage() {
        assertThat(Basis.module("facturation-api").ref()).isEqualTo("facturation-api");
        assertThat(Basis.NONE.ref()).isNull();
        assertThatThrownBy(() -> new Basis(BasisKind.MODULE, " ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Basis(BasisKind.NONE, "UC-01")).isInstanceOf(DomainException.class);
    }

    @Test
    void uneTacheCreeeAUneCle() {
        SpecTask t = proposed("Titre", "Résultat");
        assertThat(t.creation()).isEqualTo(TaskCreation.NOT_CREATED);
        assertThat(t.created("FACT-124").jiraKey()).isEqualTo("FACT-124");
        refused(() -> t.created("pas une clé"));
    }

    @Test
    void priseTermineeLiberee() {
        Holder h = new Holder("4242-1700000000000", "claude\u0007-code", NOW);
        assertThat(h.agentName()).isEqualTo("claude -code");
        SpecTask started = proposed("Titre", "Résultat").created("FACT-124").started(h, true);
        assertThat(started.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(started.holder()).isEqualTo(h);
        assertThat(started.pendingTarget()).isEqualTo(TaskStatus.IN_PROGRESS);
        SpecTask done = started.completed("Fait", false);
        assertThat(done.status()).isEqualTo(TaskStatus.DONE);
        assertThat(done.holder()).isNull();
        assertThat(done.pendingTarget()).isNull();
        assertThat(done.report()).isEqualTo("Fait");
        SpecTask doneOffline = started.completed("Fait", true);
        assertThat(doneOffline.pendingTarget()).isEqualTo(TaskStatus.DONE);
        assertThat(doneOffline.pendingComment()).isTrue();
        assertThat(started.completed(null, true).pendingComment()).isFalse();
        SpecTask released = started.released(false);
        assertThat(released.status()).isEqualTo(TaskStatus.TODO);
        assertThat(released.holder()).isNull();
        assertThat(released.synced().pendingTarget()).isNull();
    }

    @Test
    void leCompteRenduEstBorne() {
        SpecTask started = proposed("Titre", "Résultat").created("FACT-124")
                .started(new Holder("1-2", "agent", NOW), false);
        assertThatThrownBy(() -> started.completed("x".repeat(2001), false)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REPORT_TOO_LONG));
        assertThat(started.completed("  ", false).report()).isNull();
    }

    @Test
    void laRelectureDeJiraSImpose() {
        SpecTask started = proposed("Titre", "Résultat").created("FACT-124")
                .started(new Holder("1-2", "agent", NOW), false);
        SpecTask doneInJira = started.read(TaskStatus.DONE, "Fermé", NOW);
        assertThat(doneInJira.status()).isEqualTo(TaskStatus.DONE);
        assertThat(doneInJira.holder()).isNull();
        assertThat(doneInJira.jiraStatusName()).isEqualTo("Fermé");
        assertThat(doneInJira.lastReadAt()).isEqualTo(NOW);
        assertThat(started.read(TaskStatus.IN_PROGRESS, "En cours", NOW).holder()).isNotNull();
        assertThat(started.read(TaskStatus.MISSING, null, NOW).holder()).isNull();
    }
}
