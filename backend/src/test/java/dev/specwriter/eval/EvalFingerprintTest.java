package dev.specwriter.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** T138 : l'empreinte de la suite d'évaluation et celle de ci/check-eval.sh sont identiques. */
class EvalFingerprintTest {

    @Test
    void memeEmpreinteQueLeScriptDeLaCi() throws IOException, InterruptedException {
        Path script = EvalRuns.REPO.resolve("ci/check-eval.sh");
        assumeThat(Files.isExecutable(script) && !System.getProperty("os.name", "").startsWith("Windows")).isTrue();
        Process p = new ProcessBuilder(script.toString(), "--fingerprint").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).as(out).isZero();

        assertThat(out).isEqualTo(EvalFingerprint.compute(EvalRuns.REPO));
    }

    @Test
    void lEmpreinteCouvreLesPromptsLeSdkEtLeModeleParDefaut() {
        String manifest = EvalFingerprint.manifest(EvalRuns.REPO);

        assertThat(manifest)
                .contains("  backend/src/main/resources/prompts/propose-questions.txt\n")
                .contains("  backend/src/main/java/dev/specwriter/infrastructure/llm/CopilotRuntime.java\n")
                .contains("  backend/src/test/resources/golden/01-reservation-salle/need.md\n")
                .contains("  backend/src/test/resources/eval/prompts/judge-questions.txt\n")
                .doesNotContain("last-run.json")
                .doesNotContain("baseline.json")
                .contains("copilot-sdk=" + EvalFingerprint.copilotSdk(EvalRuns.REPO) + "\n")
                .endsWith("default-model=" + EvalFingerprint.defaultModel(EvalRuns.REPO) + "\n");
        assertThat(EvalFingerprint.copilotSdk(EvalRuns.REPO)).isNotBlank();
    }
}
