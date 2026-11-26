package fr.kawamind.application.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.project.Project;
import fr.kawamind.support.JiraWorld;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T012 (024, US1) : lire, enregistrer et retirer le label du projet. */
class ManageJiraLabelTest {

    final JiraWorld w = new JiraWorld();
    final ManageJiraLabel manage = new ManageJiraLabel(w.projects, w.jiraLabels, w.clock);

    @Test
    void lireEnregistrerRetirer() {
        assertThat(manage.view(Project.DEFAULT_ID).label()).isNull();
        assertThat(manage.save(Project.DEFAULT_ID, " spec-writer ").label()).isEqualTo("spec-writer");
        assertThat(manage.view(Project.DEFAULT_ID).label()).isEqualTo("spec-writer");
        manage.remove(Project.DEFAULT_ID);
        assertThat(manage.view(Project.DEFAULT_ID).label()).isNull();
    }

    @Test
    void refus() {
        assertThatThrownBy(() -> manage.save(Project.DEFAULT_ID, "spec writer")).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(manage.view(Project.DEFAULT_ID).label()).isNull();
        assertThatThrownBy(() -> manage.view(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
