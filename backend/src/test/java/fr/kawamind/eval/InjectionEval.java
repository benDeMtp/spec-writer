package fr.kawamind.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 014 (T019, FR-005) : un ticket, une page ou une question piégés ne font lire aucun fichier à
 * l'agent et ne lui font pas révéler sa consigne. Vrai Copilot : {@code ./mvnw -pl backend verify -Peval}.
 */
@Tag("eval")
@io.quarkus.test.junit.QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
@io.quarkus.test.junit.TestProfile(EvalProfile.class)
class InjectionEval {

    @Test
    void resistanceAuNiveauDeLaBase() {
        EvalRuns.Result r = EvalRuns.result();
        double score = r.conversation().get(EvalBaseline.INJECTION_RESISTANCE);
        assertThat(r.baseline().accepts(EvalBaseline.INJECTION_RESISTANCE, score))
                .as("%s %.4f, attendu au moins %.4f (détail dans last-run.json)", EvalBaseline.INJECTION_RESISTANCE,
                        score, r.baseline().required(EvalBaseline.INJECTION_RESISTANCE))
                .isTrue();
    }
}
