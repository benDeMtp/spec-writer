package dev.specwriter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.copilot.rpc.ModelBilling;
import com.github.copilot.rpc.ModelCapabilities;
import com.github.copilot.rpc.ModelInfo;
import com.github.copilot.rpc.ModelLimits;
import com.github.copilot.rpc.ModelPolicy;
import dev.specwriter.domain.port.GitHubAuthenticator.ModelOption;
import org.junit.jupiter.api.Test;

/** Lecture de la capacité et du coefficient d'un modèle du SDK (008, research.md R1). */
class SdkCopilotAccountGatewayTest {

    @Test
    void limitesEtCoefficientLus() {
        ModelInfo info = new ModelInfo().setId("claude-sonnet-5.5").setName("Claude Sonnet 5.5")
                .setPolicy(new ModelPolicy().setState("enabled"))
                .setCapabilities(new ModelCapabilities()
                        .setLimits(new ModelLimits().setMaxPromptTokens(128_000).setMaxContextWindowTokens(200_000)))
                .setBilling(new ModelBilling().setMultiplier(0.33));
        assertThat(SdkCopilotAccountGateway.option(info))
                .isEqualTo(new ModelOption("claude-sonnet-5.5", "Claude Sonnet 5.5", true, 128_000, 200_000, 0.33));
    }

    @Test
    void sansCapaciteNiFacturationRienNePlante() {
        ModelOption bare = SdkCopilotAccountGateway.option(new ModelInfo().setId("auto"));
        assertThat(bare).isEqualTo(new ModelOption("auto", null, true, null, null, null));
        ModelOption empty = SdkCopilotAccountGateway.option(new ModelInfo().setId("x")
                .setPolicy(new ModelPolicy().setState("disabled"))
                .setCapabilities(new ModelCapabilities().setLimits(new ModelLimits())).setBilling(new ModelBilling()));
        assertThat(empty.enabled()).isFalse();
        assertThat(empty.capacity().promptTokens()).isNull();
        assertThat(empty.capacity().contextTokens()).isNull();
    }
}
