package dev.specwriter.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.ConversationLlm.ChangeCandidate;
import dev.specwriter.domain.port.ConversationLlm.ChangeOrigin;
import dev.specwriter.domain.port.ConversationLlm.Revision;
import dev.specwriter.support.ScriptedConversationLlm;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 011 (T031) : le volet « discussion » de l'évaluation tourne de bout en bout, sans Copilot. */
class DiscussionRunsTest {

    @Test
    void chaqueCasDonneSesControlesEtSesScores() {
        ScriptedConversationLlm llm = new ScriptedConversationLlm();
        llm.revisions.add(new Revision(false, "Rien d'établi.", null));
        llm.changes.add(List.of(new ChangeCandidate("Passer la première relance à 7 jours", ChangeOrigin.USER)));

        List<DiscussionRuns.CaseRun> runs = DiscussionRuns.run(llm, 1);

        assertThat(runs).extracting(DiscussionRuns.CaseRun::caseName).contains("citation-contestee",
                "incoherence-relance-postale", "revision-delai", "sans-changement-questions");
        assertThat(llm.answerRequests).allSatisfy(r -> assertThat(r.discussed()).isNotNull());
        assertThat(llm.reviseRequests).hasSize(4)
                .allSatisfy(r -> assertThat(r.currentMarkdown()).contains("Relances des factures impayées"));
        // l'agent scripté ne cite rien et ne propose rien : seules les discussions sans changement passent
        assertThat(DiscussionRuns.score(runs, EvalBaseline.DISCUSSION_CITATION, 1)).isZero();
        assertThat(DiscussionRuns.score(runs, EvalBaseline.REVISION_COMPLETENESS, 1)).isZero();
        assertThat(DiscussionRuns.score(runs, EvalBaseline.REVISION_STRUCTURE, 1)).isEqualTo(1.0);
        assertThat(DiscussionRuns.score(runs, EvalBaseline.UNCHANGED_RECALL, 1)).isEqualTo(1.0);
        // le même relevé pour tous : il couvre la révision du délai, pas celle du SMS, et accuse à tort les autres
        assertThat(llm.changesRequests).hasSize(4);
        assertThat(DiscussionRuns.score(runs, EvalBaseline.CHANGES_RECALL, 1)).isEqualTo(0.5);
        assertThat(DiscussionRuns.score(runs, EvalBaseline.CHANGES_PRECISION, 1)).isZero();
    }
}
