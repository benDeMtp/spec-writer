package dev.specwriter.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class CopilotSettingsTest {

    @Test
    void modeleParDefautSelonLaPreference() {
        CopilotSettings s = CopilotSettings.initial().connected("ada", List.of("auto", "gpt-6.1-sol", "claude-sonnet-5.5"));
        assertThat(s.model()).isEqualTo("claude-sonnet-5.5");
        assertThat(s.subscriptionState()).isEqualTo(CopilotSettings.SubscriptionState.ACTIVE);
        assertThat(s.connectedLogin()).isEqualTo("ada");
    }

    @Test
    void sansPreferenceDisponibleLePremierModele() {
        assertThat(CopilotSettings.initial().connected("ada", List.of("kimi-k3", "mai-code")).model()).isEqualTo("kimi-k3");
    }

    @Test
    void aucunModeleSignifieSansCopilot() {
        CopilotSettings s = CopilotSettings.initial().connected("ada", List.of());
        assertThat(s.subscriptionState()).isEqualTo(CopilotSettings.SubscriptionState.NO_COPILOT);
        assertThat(s.model()).isNull();
    }

    @Test
    void choixConserveSIlResteDisponible() {
        CopilotSettings s = CopilotSettings.initial().connected("ada", List.of("a", "b")).withModel("b");
        assertThat(s.connected("ada", List.of("a", "b", "c")).model()).isEqualTo("b");
        assertThat(s.connected("ada", List.of("a")).model()).isEqualTo("a");
    }

    @Test
    void modeleHorsListeRefuse() {
        CopilotSettings s = CopilotSettings.initial().connected("ada", List.of("a"));
        assertThatThrownBy(() -> s.withModel("z")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.MODEL_UNAVAILABLE);
    }

    @Test
    void deconnexion() {
        CopilotSettings s = CopilotSettings.initial().connected("ada", List.of("a")).disconnected();
        assertThat(s.connectedLogin()).isNull();
        assertThat(s.availableModels()).isEmpty();
        assertThat(s.model()).isNull();
    }

    @Test
    void capacitesGardeesAuChoixDuModeleEtPerduesALaDeconnexion() {
        CopilotSettings s = CopilotSettings.initial().connected("ada", List.of("a", "b"),
                List.of(new ModelCapacity("a", "A", 128_000, null, 1.0), new ModelCapacity("b", "B", 32_000, null, 0.33)));
        assertThat(s.withModel("b").capacity()).hasValueSatisfying(c -> assertThat(c.promptTokens()).isEqualTo(32_000));
        assertThat(s.withModel("b").capacities()).hasSize(2);
        assertThat(s.disconnected().capacities()).isEmpty();
        assertThat(CopilotSettings.initial().capacity()).isEmpty();
        assertThat(s.connected("ada", List.of()).capacities()).isEmpty();
    }
}
