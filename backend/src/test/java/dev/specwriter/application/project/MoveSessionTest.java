package dev.specwriter.application.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.JiraWorld;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Déplacer une spécification vers un autre projet, avec ses versions (FR-004, T012). */
class MoveSessionTest {

    final JiraWorld w = new JiraWorld();
    final MoveSession move = new MoveSession(w.sessions, w.projects, w.clock);
    final ManageSessions manage = new ManageSessions(w.sessions, w.versions, w.projects, w.clock);

    @Test
    void laSessionEtSesVersionsChangentDeProjet() {
        Project facturation = new CreateProject(w.projects, w.clock, UUID::randomUUID).run("Facturation", null, null);
        Session s = w.generatedSession();
        w.clock.advance(java.time.Duration.ofMinutes(1));

        move.run(s.id(), facturation.id());

        Session moved = w.sessions.find(s.id()).orElseThrow();
        assertThat(moved.projectId()).isEqualTo(facturation.id());
        assertThat(moved.updatedAt()).isAfter(s.updatedAt());
        assertThat(moved.name()).isEqualTo(s.name());
        assertThat(w.versions.list(s.id())).hasSize(1);
        assertThat(w.sessions.questionnaire(s.id())).isPresent();

        // la liste du projet courant ne la montre plus ; celle du projet cible, si
        assertThat(manage.list()).noneMatch(e -> e.session().id().equals(s.id()));
        w.projects.saveCurrentProjectId(facturation.id());
        assertThat(manage.list()).singleElement().satisfies(e -> assertThat(e.session().id()).isEqualTo(s.id()));
    }

    @Test
    void sessionOuProjetInconnu() {
        Session s = w.generatedSession();
        assertThatThrownBy(() -> move.run(s.id(), UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        assertThatThrownBy(() -> move.run(UUID.randomUUID(), Project.DEFAULT_ID)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(w.sessions.find(s.id()).orElseThrow().projectId()).isEqualTo(Project.DEFAULT_ID);
    }
}
