package fr.kawamind.domain.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import org.junit.jupiter.api.Test;

/** T003 (024, research R4) : un label Jira valide, comparé tel quel. */
class JiraLabelTest {

    @Test
    void unLabelEstRogneEtCompareTelQuel() {
        assertThat(JiraLabel.of("  spec-writer ").value()).isEqualTo("spec-writer");
        assertThat(JiraLabel.of("spec-writer")).isEqualTo(JiraLabel.of("spec-writer")).isNotEqualTo(JiraLabel.of("Spec-Writer"));
    }

    @Test
    void videTropLongOuAvecUnBlancEstRefuse() {
        for (String bad : new String[] {null, " ", "spec writer", "spec\twriter", "x".repeat(256)}) {
            assertThatThrownBy(() -> JiraLabel.of(bad)).isInstanceOfSatisfying(DomainException.class, e -> {
                assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                assertThat(e.userMessage())
                        .isEqualTo("Un label Jira ne contient pas d'espace et compte au plus 255 caractères.");
            });
        }
        assertThat(JiraLabel.of("x".repeat(255)).value()).hasSize(255);
    }
}
