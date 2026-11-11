package dev.specwriter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 011 (T031) : l'agent cite la spécification discutée par section, signale son écart à un ADR, et
 * propose une version qui contient les changements établis, garde le reste et passe le contrôle de
 * structure ; sans changement établi, il ne propose rien. Vrai Copilot :
 * {@code ./mvnw -pl backend verify -Peval}.
 */
@Tag("eval")
class DiscussionEval {

    @ParameterizedTest
    @ValueSource(strings = {EvalBaseline.DISCUSSION_CITATION, EvalBaseline.DISCUSSION_INCONSISTENCY,
        EvalBaseline.REVISION_COMPLETENESS, EvalBaseline.REVISION_PRESERVATION, EvalBaseline.REVISION_STRUCTURE,
        EvalBaseline.UNCHANGED_RECALL, EvalBaseline.CHANGES_RECALL, EvalBaseline.CHANGES_PRECISION})
    void scoreAuNiveauDeLaBase(String metric) {
        EvalRuns.Result r = EvalRuns.result();
        double score = r.conversation().get(metric);
        assertThat(r.baseline().accepts(metric, score))
                .as("%s %.4f, attendu au moins %.4f (détail dans last-run.json)", metric, score,
                        r.baseline().required(metric))
                .isTrue();
    }
}
