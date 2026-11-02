package dev.specwriter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SC-002 (T137) : sur le golden set, 100 % des scénarios générés passent le contrôle de structure.
 * Une génération en échec compte comme un scénario non conforme. Vrai Copilot :
 * {@code ./mvnw -pl backend verify -Peval}.
 */
@Tag("eval")
class SpecificationStructureEval {

    @Test
    void tousLesScenariosSontConformes() {
        EvalRuns.Result r = EvalRuns.result();
        assertThat(r.baseline().accepts(EvalBaseline.SCENARIO_STRUCTURE, r.scenarioStructure()))
                .as("part de scénarios conformes %.4f, attendu au moins %.4f (détail dans last-run.json)",
                        r.scenarioStructure(), r.baseline().required(EvalBaseline.SCENARIO_STRUCTURE))
                .isTrue();
    }
}
