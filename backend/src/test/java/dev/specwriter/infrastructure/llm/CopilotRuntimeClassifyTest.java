package dev.specwriter.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.infrastructure.llm.CopilotSdkGateway.GatewayException.Kind;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class CopilotRuntimeClassifyTest {

    private static Kind kind(Throwable t) {
        return CopilotRuntime.classify(t).kind();
    }

    @Test
    void classement() {
        assertThat(kind(new ExecutionException(new RuntimeException("Execution failed: 429 quota exceeded")))).isEqualTo(Kind.QUOTA_EXHAUSTED);
        assertThat(kind(new RuntimeException("The requested model is not supported"))).isEqualTo(Kind.MODEL_UNAVAILABLE);
        assertThat(kind(new RuntimeException("Model disabled by policy"))).isEqualTo(Kind.MODEL_UNAVAILABLE);
        assertThat(kind(new RuntimeException("Not authenticated"))).isEqualTo(Kind.NOT_CONNECTED);
        assertThat(kind(new TimeoutException())).isEqualTo(Kind.UNAVAILABLE);
        assertThat(kind(new RuntimeException("connexion réinitialisée"))).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void methodeInconnueDuCliSignaleUnCliTropAncien() {
        // CLI 1.0.11 face au SDK 1.0.28 : il ignore les méthodes ajoutées depuis
        assertThat(kind(new ExecutionException(new RuntimeException("Unhandled method connect")))).isEqualTo(Kind.CLI_OUTDATED);
        assertThat(kind(new RuntimeException("Unhandled method session.accounts.login.begin"))).isEqualTo(Kind.CLI_OUTDATED);
    }

    @Test
    void leMessageDOrigineNEstPasRepris() {
        assertThat(CopilotRuntime.classify(new RuntimeException("besoin confidentiel")).getMessage()).isEqualTo("UNAVAILABLE");
    }
}
