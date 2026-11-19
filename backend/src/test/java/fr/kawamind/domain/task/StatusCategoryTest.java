package fr.kawamind.domain.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T006 (015, research R3, R6) : catégories de statut de Jira et statuts des tâches. */
class StatusCategoryTest {

    @Test
    void lesCleesJiraCorrespondentAuxStatuts() {
        assertThat(StatusCategory.fromKey("new")).contains(StatusCategory.NEW);
        assertThat(StatusCategory.fromKey("indeterminate")).contains(StatusCategory.IN_PROGRESS);
        assertThat(StatusCategory.fromKey("done")).contains(StatusCategory.DONE);
        assertThat(StatusCategory.fromKey("undefined")).isEmpty();
        assertThat(StatusCategory.fromKey(null)).isEmpty();
        assertThat(StatusCategory.NEW.status()).isEqualTo(TaskStatus.TODO);
        assertThat(StatusCategory.DONE.key()).isEqualTo("done");
        assertThat(StatusCategory.of(TaskStatus.IN_PROGRESS)).isEqualTo(StatusCategory.IN_PROGRESS);
        assertThat(StatusCategory.of(TaskStatus.TODO)).isEqualTo(StatusCategory.NEW);
    }
}
