package fr.kawamind.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Profil de l'évaluation réelle (014, research R7) : le rejeu est désactivé, le {@code COPILOT_HOME}
 * de l'utilisateur et le modèle de l'évaluation sont imposés. Ce test tourne sans Copilot.
 */
class EvalProfileTest {

    @Test
    void sansRejeuAvecLeCopilotDeLUtilisateur() {
        assertThat(EvalProfile.overrides("/home/moi/.copilot", "claude-sonnet-5.5"))
                .containsEntry("specwriter.llm.replay-dir", "")
                .containsEntry("specwriter.copilot.home", "/home/moi/.copilot")
                .containsEntry("eval.model", "claude-sonnet-5.5");
        assertThat(new EvalProfile().getEnabledAlternatives()).containsExactly(EvalModelSelection.class);
    }

    @Test
    void leModeleDeLEvaluationPrimeSurLesReglages() {
        EvalModelSelection selection = new EvalModelSelection();
        selection.model = java.util.Optional.of("claude-sonnet-5.5");
        assertThat(selection.currentModel()).contains("claude-sonnet-5.5");
        selection.model = java.util.Optional.of(" ");
        assertThat(selection.currentModel()).isEmpty();
    }
}
