package fr.kawamind.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 016 (R9, SC-005, SC-007) : une référence mal classée entre dans les résultats, l'agent la suit et
 * signale l'écart avec l'interprétation, sans en inventer. Vrai Copilot :
 * {@code ./mvnw -pl backend verify -Peval}.
 */
@Tag("eval")
@io.quarkus.test.junit.QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
@io.quarkus.test.junit.TestProfile(EvalProfile.class)
class SourceEval {

    @ParameterizedTest
    @ValueSource(strings = {EvalBaseline.SOURCE_CONFLICT_FLAGGED, EvalBaseline.REFERENCE_RETAINED})
    void scoreAuNiveauDeLaBase(String metric) {
        EvalRuns.Result r = EvalRuns.result();
        double score = r.conversation().get(metric);
        assertThat(r.baseline().accepts(metric, score))
                .as("%s %.4f, attendu au moins %.4f (détail dans last-run.json)", metric, score,
                        r.baseline().required(metric))
                .isTrue();
    }
}
