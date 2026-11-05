package dev.specwriter.application.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.JiraWorld;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Gestion des projets (US1, FR-001, FR-002, FR-005, T011). */
class ManageProjectsTest {

    final JiraWorld w = new JiraWorld();
    final OperationRegistry operations = new OperationRegistry();
    CreateProject create;
    UpdateProject update;
    DeleteProject delete;
    SelectCurrentProject select;

    @BeforeEach
    void setUp() {
        create = new CreateProject(w.projects, w.clock, UUID::randomUUID);
        update = new UpdateProject(w.projects, w.clock);
        delete = new DeleteProject(w.projects, w.sessions,
                new ManageSessions(w.sessions, w.versions, w.projects, w.clock), operations, w.clock);
        select = new SelectCurrentProject(w.projects);
    }

    static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(expected));
    }

    Session sessionIn(Project p) {
        Session s = w.generatedSession();
        Session moved = s.movedTo(p.id(), w.clock.now());
        w.sessions.save(moved, w.sessions.questionnaire(s.id()).orElseThrow());
        return moved;
    }

    @Test
    void creationEtRenommage() {
        Project p = create.run("  Facturation ", "Factures et avoirs", "FACT");
        assertThat(p.name().value()).isEqualTo("Facturation");
        assertThat(p.jiraProjectKey().value()).isEqualTo("FACT");
        assertThat(w.projects.find(p.id())).contains(p);

        Project renamed = update.run(p.id(), "Facturation clients", null, null);
        assertThat(renamed.name().value()).isEqualTo("Facturation clients");
        assertThat(renamed.description()).isNull();
        assertThat(renamed.jiraProjectKey()).isNull();
        assertThat(renamed.createdAt()).isEqualTo(p.createdAt());
        // garder son propre nom, même en changeant la casse, n'est pas un doublon
        assertThat(update.run(p.id(), "FACTURATION CLIENTS", null, null).name().value()).isEqualTo("FACTURATION CLIENTS");
    }

    @Test
    void nomEnDoubleRefuseSansTenirCompteDeLaCasse() {
        Project p = create.run("Facturation", null, null);
        assertCode(() -> create.run("FACTURATION", null, null), ErrorCode.PROJECT_NAME_TAKEN);
        assertCode(() -> create.run("projet par défaut", null, null), ErrorCode.PROJECT_NAME_TAKEN);
        assertCode(() -> update.run(p.id(), "Projet par défaut", null, null), ErrorCode.PROJECT_NAME_TAKEN);
        assertCode(() -> update.run(UUID.randomUUID(), "Paie", null, null), ErrorCode.PROJECT_NOT_FOUND);
        assertCode(() -> create.run("Paie", null, "fact"), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void suppressionEnDeplacantLesSessions() {
        Project p = create.run("Facturation", null, "FACT");
        Session a = sessionIn(p);
        Session b = sessionIn(p);
        delete.run(p.id(), DeleteProject.Mode.MOVE, Project.DEFAULT_ID);
        assertThat(w.projects.find(p.id())).isEmpty();
        assertThat(w.sessions.find(a.id()).orElseThrow().projectId()).isEqualTo(Project.DEFAULT_ID);
        assertThat(w.sessions.find(b.id()).orElseThrow().projectId()).isEqualTo(Project.DEFAULT_ID);
        assertThat(w.versions.list(a.id())).hasSize(1);
    }

    @Test
    void suppressionAvecLesSessions() {
        Project p = create.run("Facturation", null, null);
        Session a = sessionIn(p);
        Session elsewhere = w.generatedSession();
        delete.run(p.id(), DeleteProject.Mode.DELETE, null);
        assertThat(w.projects.find(p.id())).isEmpty();
        assertThat(w.sessions.find(a.id())).isEmpty();
        assertThat(w.versions.list(a.id())).isEmpty();
        assertThat(w.sessions.find(elsewhere.id())).isPresent();
    }

    @Test
    void deplacementVersUnProjetValide() {
        Project p = create.run("Facturation", null, null);
        sessionIn(p);
        assertCode(() -> delete.run(p.id(), DeleteProject.Mode.MOVE, null), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> delete.run(p.id(), DeleteProject.Mode.MOVE, p.id()), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> delete.run(p.id(), DeleteProject.Mode.MOVE, UUID.randomUUID()), ErrorCode.PROJECT_NOT_FOUND);
        assertCode(() -> delete.run(UUID.randomUUID(), DeleteProject.Mode.DELETE, null), ErrorCode.PROJECT_NOT_FOUND);
        assertThat(w.projects.find(p.id())).isPresent();
    }

    @Test
    void leDernierProjetRestantNePeutPasEtreSupprime() {
        assertCode(() -> delete.run(Project.DEFAULT_ID, DeleteProject.Mode.DELETE, null), ErrorCode.LAST_PROJECT);
        assertThat(w.projects.count()).isEqualTo(1);
    }

    @Test
    void suppressionRefuseePendantUneOperationIaEnCours() {
        Project p = create.run("Facturation", null, null);
        Session a = sessionIn(p);
        try (OperationRegistry.Operation op = operations.begin(a.id())) {
            assertCode(() -> delete.run(p.id(), DeleteProject.Mode.DELETE, null), ErrorCode.OPERATION_IN_PROGRESS);
        }
        delete.run(p.id(), DeleteProject.Mode.DELETE, null);
        assertThat(w.projects.find(p.id())).isEmpty();
    }

    @Test
    void projetCourantConserveEtPremierParNomSiSupprime() {
        Project paie = create.run("Paie", null, null);
        Project achats = create.run("Achats", null, null);
        assertThat(CurrentProject.resolve(w.projects).id()).isEqualTo(Project.DEFAULT_ID);
        select.run(paie.id());
        assertThat(CurrentProject.resolve(w.projects).id()).isEqualTo(paie.id());
        assertCode(() -> select.run(UUID.randomUUID()), ErrorCode.PROJECT_NOT_FOUND);
        assertThat(CurrentProject.resolve(w.projects).id()).isEqualTo(paie.id());

        delete.run(paie.id(), DeleteProject.Mode.DELETE, null);
        assertThat(CurrentProject.resolve(w.projects).id()).isEqualTo(achats.id());
    }
}
