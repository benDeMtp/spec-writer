package dev.specwriter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** T137, T138 : un score atteint le plus haut de son minimum et de sa base moins la tolérance. */
class EvalBaselineTest {

    private final EvalBaseline baseline = new EvalBaseline(0.02,
            Map.of("questionRelevance", 0.9, "scenarioStructure", 1.0),
            Map.of("questionRelevance", 0.95, "scenarioStructure", 1.0));

    @Test
    void laToleranceAbsorbeUnePetiteVariation() {
        assertThat(baseline.required("questionRelevance")).isEqualTo(0.93, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(baseline.accepts("questionRelevance", 0.93)).isTrue();
        assertThat(baseline.accepts("questionRelevance", 0.92)).isFalse();
    }

    @Test
    void leMinimumDuCritereDeSuccesNeFlechitPas() {
        assertThat(baseline.accepts("scenarioStructure", 1.0)).isTrue();
        assertThat(baseline.accepts("scenarioStructure", 0.99)).isFalse();
        EvalBaseline low = new EvalBaseline(0.02, Map.of("questionRelevance", 0.9), Map.of("questionRelevance", 0.9));
        assertThat(low.accepts("questionRelevance", 0.89)).isFalse();
    }

    @Test
    void laBaseDuDepotEstLisible() {
        EvalBaseline committed = EvalRuns.readBaseline();

        assertThat(committed.required(EvalBaseline.QUESTION_RELEVANCE)).isGreaterThanOrEqualTo(0.9);
        assertThat(committed.required(EvalBaseline.SCENARIO_STRUCTURE)).isEqualTo(1.0);
    }
}
