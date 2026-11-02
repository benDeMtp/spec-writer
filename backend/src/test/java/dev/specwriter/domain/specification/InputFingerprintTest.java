package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.session.Language;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputFingerprintTest {

    static final String NEED = "Un besoin de test";

    @Test
    void stableEtIndependanteDeLOrdre() {
        List<Question> treated = IdentifierAssignerTest.treated();
        List<Question> reversed = new ArrayList<>(treated).reversed();
        String a = InputFingerprint.of(NEED, treated, "0.1.0", Language.FR);
        assertThat(a).hasSize(64).isEqualTo(InputFingerprint.of(NEED, reversed, "0.1.0", Language.FR));
    }

    @Test
    void changeAvecChaqueEntree() {
        List<Question> treated = IdentifierAssignerTest.treated();
        String base = InputFingerprint.of(NEED, treated, "0.1.0", Language.FR);
        assertThat(InputFingerprint.of(NEED + ".", treated, "0.1.0", Language.FR)).isNotEqualTo(base);
        assertThat(InputFingerprint.of(NEED, treated, "0.2.0", Language.FR)).isNotEqualTo(base);
        assertThat(InputFingerprint.of(NEED, treated, "0.1.0", Language.EN)).isNotEqualTo(base);
        assertThat(InputFingerprint.of(NEED, treated.subList(0, 1), "0.1.0", Language.FR)).isNotEqualTo(base);
    }

    @Test
    void decoupageAmbiguImpossible() {
        assertThat(InputFingerprint.of("ab", List.of(), "c", Language.FR))
                .isNotEqualTo(InputFingerprint.of("a", List.of(), "bc", Language.FR));
    }
}
