package fr.kawamind.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 021, research.md R2 : une question sans réponse ni valeur par défaut, bornée. */
class OpenQuestionTest {

    @Test
    void bornes() {
        OpenQuestion q = new OpenQuestion("Q-01", "  Qui valide en l'absence du manager ?  ", " Paul D. ",
                LocalDate.of(2026, 11, 27), new Anchor("UC-01", 3, false), null);
        assertThat(q.question()).isEqualTo("Qui valide en l'absence du manager ?");
        assertThat(q.owner()).isEqualTo("Paul D.");
        assertThat(new OpenQuestion("Q-01", "x".repeat(1_000), "", null, null, null).owner()).isNull();

        assertThatThrownBy(() -> new OpenQuestion("Q-01", " ", null, null, null, null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> new OpenQuestion("Q-01", "x".repeat(1_001), null, null, null, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new OpenQuestion("Q-01", "Qui ?", "x".repeat(121), null, null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void auPlusCinquanteParVersion() {
        List<OpenQuestion> many = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            many.add(new OpenQuestion("Q-%02d".formatted(i), "Question " + i, null, null, null, null));
        }
        SpecificationContent base = Specs.valid();
        assertThat(base.withOpenQuestions(many.subList(0, 50)).openQuestions()).hasSize(50);
        assertThatThrownBy(() -> base.withOpenQuestions(many)).isInstanceOf(DomainException.class);
        assertThat(base.openQuestions()).isEmpty();
    }
}
