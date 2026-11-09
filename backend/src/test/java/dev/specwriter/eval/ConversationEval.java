package dev.specwriter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 010 (T027) : l'agent cite la source attendue, s'abstient hors de la connaissance du projet et
 * signale les paires contradictoires avec leurs deux sources. Vrai Copilot :
 * {@code ./mvnw -pl backend verify -Peval}.
 */
@Tag("eval")
class ConversationEval {

    @ParameterizedTest
    @ValueSource(strings = {EvalBaseline.CONVERSATION_CITATION, EvalBaseline.CONVERSATION_ABSTENTION,
        EvalBaseline.INCONSISTENCY_RECALL})
    void scoreAuNiveauDeLaBase(String metric) {
        EvalRuns.Result r = EvalRuns.result();
        double score = r.conversation().get(metric);
        assertThat(r.baseline().accepts(metric, score))
                .as("%s %.4f, attendu au moins %.4f (détail dans last-run.json)", metric, score,
                        r.baseline().required(metric))
                .isTrue();
    }
}
