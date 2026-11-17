package fr.kawamind.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 015 (T076, SC-001, SC-008) : la proposition de tâches respecte ses bornes, cite ses bases telles
 * quelles, couvre les modules impactés et donne des résultats vérifiables. Vrai Copilot :
 * {@code ./mvnw -pl backend verify -Peval}.
 */
@Tag("eval")
@io.quarkus.test.junit.QuarkusTest
@io.quarkus.test.junit.TestProfile(EvalProfile.class)
class TaskEval {

    @Test
    void propositionDeTachesAuNiveauDeLaBase() {
        EvalRuns.Result r = EvalRuns.result();
        for (String metric : List.of(EvalBaseline.TASK_BOUNDS, EvalBaseline.TASK_BASIS_EXACT,
                EvalBaseline.TASK_MODULE_COVERAGE, EvalBaseline.TASK_VERIFIABLE)) {
            double score = r.conversation().get(metric);
            assertThat(r.baseline().accepts(metric, score))
                    .as("%s %.4f, attendu au moins %.4f (détail dans last-run.json)", metric, score,
                            r.baseline().required(metric))
                    .isTrue();
        }
    }
}
