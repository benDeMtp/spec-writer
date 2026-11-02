package dev.specwriter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SC-004 (T137) : sur le golden set, au plus 10 % des questions posées sont redondantes ou hors sujet,
 * et la part de questions pertinentes ne recule pas sous la base. Vrai Copilot :
 * {@code ./mvnw -pl backend verify -Peval}.
 */
@Tag("eval")
class QuestionRelevanceEval {

    @Test
    void lesQuestionsRestentPertinentes() {
        EvalRuns.Result r = EvalRuns.result();
        assertThat(r.baseline().accepts(EvalBaseline.QUESTION_RELEVANCE, r.questionRelevance()))
                .as("part de questions pertinentes %.4f, attendu au moins %.4f (détail dans last-run.json)",
                        r.questionRelevance(), r.baseline().required(EvalBaseline.QUESTION_RELEVANCE))
                .isTrue();
    }
}
