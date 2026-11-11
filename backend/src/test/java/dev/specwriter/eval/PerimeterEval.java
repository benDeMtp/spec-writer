package dev.specwriter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 013 (T034) : les termes proposés par l'agent retrouvent les modules attendus, et sa qualification
 * rejoint celle attendue. Vrai Copilot : {@code ./mvnw -pl backend verify -Peval}.
 */
@Tag("eval")
class PerimeterEval {

    @ParameterizedTest
    @ValueSource(strings = {EvalBaseline.PERIMETER_TERM_RECALL, EvalBaseline.PERIMETER_QUALIFICATION})
    void scoreAuNiveauDeLaBase(String metric) {
        EvalRuns.Result r = EvalRuns.result();
        double score = r.conversation().get(metric);
        assertThat(r.baseline().accepts(metric, score))
                .as("%s %.4f, attendu au moins %.4f (détail dans last-run.json)", metric, score,
                        r.baseline().required(metric))
                .isTrue();
    }
}
