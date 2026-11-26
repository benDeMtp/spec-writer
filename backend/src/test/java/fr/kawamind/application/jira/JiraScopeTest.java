package fr.kawamind.application.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.JiraLabel;
import fr.kawamind.domain.session.Session;
import fr.kawamind.support.JiraWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T026 (024, US3, research R5) : périmètre des agents de code d'après le label du projet. */
class JiraScopeTest {

    final JiraWorld w = new JiraWorld();
    final JiraScope scope = new JiraScope(w.jiraLabels, w.ticketLabels, w.tickets, w.sessions);
    Session linked;
    Session alone;

    @BeforeEach
    void setUp() {
        linked = w.generatedSession();
        w.link(linked, "PROJ-12", "d");
        alone = w.generatedSession();
    }

    void label() {
        w.jiraLabels.save(linked.projectId(), JiraLabel.of("spec-writer"), w.clock.now());
    }

    @Test
    void sansLabelToutEstServi() {
        assertThat(scope.feature(linked.id())).isTrue();
        assertThat(scope.task(linked.id(), "PROJ-13")).isTrue();
        assertThat(JiraScope.OPEN.feature(linked.id())).isTrue();
        assertThat(JiraScope.OPEN.task(linked.id(), "PROJ-13")).isTrue();
    }

    @Test
    void laStoryDoitPorterLeLabel() {
        label();
        assertThat(scope.feature(linked.id())).isFalse();
        assertThatThrownBy(() -> scope.requireFeature(linked.id())).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.NOT_IN_JIRA_SCOPE);
            assertThat(e.userMessage()).isEqualTo("La feature " + linked.id() + " est liée à PROJ-12, qui ne porte pas "
                    + "le label spec-writer : elle n'est pas suivie par spec-writer dans Jira.");
        });
        // posé à la main dans Jira, lu par la synchronisation
        w.ticketLabels.add(linked.projectId(), "PROJ-12", "spec-writer");
        assertThat(scope.feature(linked.id())).isTrue();
        // la casse compte, comme dans Jira
        w.jiraLabels.save(linked.projectId(), JiraLabel.of("Spec-Writer"), w.clock.now());
        assertThat(scope.feature(linked.id())).isFalse();
    }

    @Test
    void uneFeatureSansTicketLieResteServie() {
        label();
        assertThat(scope.feature(alone.id())).isTrue();
        scope.requireFeature(alone.id());
    }

    @Test
    void laSousTacheDoitPorterLeLabel() {
        label();
        assertThat(scope.task(linked.id(), "PROJ-13")).isFalse();
        assertThatThrownBy(() -> scope.requireTask(linked.id(), "PROJ-13")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.userMessage()).isEqualTo("La sous-tâche PROJ-13 ne porte pas le label spec-writer : "
                        + "elle n'est pas suivie par spec-writer."));
        w.ticketLabels.add(linked.projectId(), "PROJ-13", "spec-writer");
        assertThat(scope.task(linked.id(), "PROJ-13")).isTrue();
    }
}
