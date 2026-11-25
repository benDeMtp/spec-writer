package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.TaskRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.Holder;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskOrigin;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.domain.task.TaskTracking;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T012 (015, V26) : tâches, préalables, proposition et suivi en base. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class TaskPersistenceIT {

    static final Instant T0 = Instant.parse("2026-11-17T09:00:00Z");

    @Inject
    SessionRepository sessions;

    @Inject
    TaskRepository tasks;

    Session session;

    @BeforeEach
    void session() {
        session = Session.start(UUID.randomUUID(), Project.DEFAULT_ID,
                Need.of("Le client doit pouvoir se rétracter de son abonnement dans les quatorze jours suivant la souscription."),
                null, T0);
        sessions.save(session, Questionnaire.empty());
    }

    @AfterEach
    void cleanup() {
        sessions.delete(session.id());
    }

    @Test
    void lesTachesSontReluesALIdentiqueDansLOrdre() {
        SpecTask a = SpecTask.proposed(UUID.randomUUID(), session.id(), 0, "Exposer l'endpoint", "POST renvoie 201",
                Basis.module("facturation-api"), TaskOrigin.PROPOSED, List.of()).created("FACT-124");
        SpecTask b = SpecTask.proposed(UUID.randomUUID(), session.id(), 1, "Écran", "Le bouton apparaît",
                Basis.useCase("UC-01"), TaskOrigin.MANUAL, List.of(a.id()));
        tasks.replaceAll(session.id(), List.of(b.at(1), a));
        assertThat(tasks.tasks(session.id())).containsExactly(a, b);

        SpecTask started = a.started(new Holder("4242-1700000000000", "claude-code", T0), true);
        tasks.update(started);
        SpecTask done = started.completed("Fait, tests verts", true).read(TaskStatus.DONE, "Terminé", T0);
        tasks.update(done);
        assertThat(tasks.tasks(session.id())).containsExactly(done, b);

        tasks.replaceAll(session.id(), List.of(done.at(1), b.at(0).dependingOn(List.of())));
        assertThat(tasks.tasks(session.id())).extracting(SpecTask::title).containsExactly("Écran", "Exposer l'endpoint");
    }

    /** V33 : description d'une sous-tâche relue dans Jira, gardée à part de l'état de la tâche. */
    @Test
    void laDescriptionRelueDansJiraEstGardee() {
        SpecTask a = SpecTask.proposed(UUID.randomUUID(), session.id(), 0, "Accès Halo", "Installer sans erreur",
                Basis.NONE, TaskOrigin.PROPOSED, List.of()).created("AURORA-182");
        tasks.replaceAll(session.id(), List.of(a));
        assertThat(tasks.descriptions(session.id())).isEmpty();

        tasks.describe(a.id(), "## Accès\n\nJeton en lecture seule.");
        tasks.update(a.started(new Holder("4242-1700000000000", "claude-code", T0), false));
        assertThat(tasks.descriptions(session.id())).containsExactly(
                java.util.Map.entry(a.id(), "## Accès\n\nJeton en lecture seule."));

        tasks.describe(a.id(), null);
        assertThat(tasks.descriptions(session.id())).isEmpty();
    }

    /** V34 (022) : type de la sous-tâche relu dans Jira, gardé à part de l'état de la tâche. */
    @Test
    void leTypeRelluDansJiraEstGarde() {
        SpecTask a = SpecTask.proposed(UUID.randomUUID(), session.id(), 0, "Accès Halo", "Installer sans erreur",
                Basis.NONE, TaskOrigin.PROPOSED, List.of()).created("AURORA-182");
        tasks.replaceAll(session.id(), List.of(a));
        assertThat(tasks.issueTypes(session.id())).isEmpty();

        tasks.type(a.id(), "Sous-Tâche (AGILE)");
        tasks.update(a.started(new Holder("4242-1700000000000", "claude-code", T0), false));
        assertThat(tasks.issueTypes(session.id())).containsExactly(java.util.Map.entry(a.id(), "Sous-Tâche (AGILE)"));

        tasks.type(a.id(), null);
        assertThat(tasks.issueTypes(session.id())).isEmpty();
    }

    @Test
    void laPropositionEtLeSuiviSontGardes() {
        assertThat(tasks.proposal(session.id())).isEmpty();
        TaskProposal p = new TaskProposal(TaskProposal.Basing.USE_CASES, List.of(
                new ProposedTask("A", "R", Basis.useCase("UC-01"), List.of(), false),
                new ProposedTask("B", "R", Basis.NONE, List.of(0), true)));
        tasks.saveProposal(session.id(), p, T0);
        tasks.saveProposal(session.id(), p, T0);
        assertThat(tasks.proposal(session.id())).contains(p);
        tasks.deleteProposal(session.id());
        assertThat(tasks.proposal(session.id())).isEmpty();

        assertThat(tasks.tracking(session.id())).isEqualTo(TaskTracking.ACTIVE);
        tasks.setTracking(session.id(), TaskTracking.SUSPENDED);
        assertThat(tasks.tracking(session.id())).isEqualTo(TaskTracking.SUSPENDED);
    }

    @Test
    void toutEstSupprimeAvecLaSession() {
        tasks.replaceAll(session.id(), List.of(SpecTask.proposed(UUID.randomUUID(), session.id(), 0, "A", "R",
                Basis.useCase("UC-01"), TaskOrigin.PROPOSED, List.of())));
        sessions.delete(session.id());
        assertThat(tasks.tasks(session.id())).isEmpty();
        session = Session.start(UUID.randomUUID(), Project.DEFAULT_ID,
                Need.of("Le client doit pouvoir se rétracter de son abonnement dans les quatorze jours suivant la souscription."),
                null, T0);
        sessions.save(session, Questionnaire.empty());
    }
}
