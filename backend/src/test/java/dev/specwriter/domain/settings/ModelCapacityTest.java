package dev.specwriter.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 008, FR-002 : valeurs absentes ou aberrantes tenues pour inconnues. */
class ModelCapacityTest {

    @Test
    void valeursPlausiblesGardees() {
        ModelCapacity c = new ModelCapacity("claude-sonnet-5.5", "Claude Sonnet 5.5", 128_000, 200_000, 1.0);
        assertThat(c.prompt()).hasValue(128_000);
        assertThat(c.contextTokens()).isEqualTo(200_000);
        assertThat(c.cost()).isEqualTo(1.0);
    }

    @Test
    void valeursAberrantesInconnues() {
        ModelCapacity c = new ModelCapacity("m", null, 0, 10_000_001, -1.0);
        assertThat(c.name()).isEqualTo("m");
        assertThat(c.prompt()).isEmpty();
        assertThat(c.contextTokens()).isNull();
        assertThat(c.multiplier()).isNull();
        assertThat(c.cost()).isEqualTo(Double.MAX_VALUE);
        assertThat(new ModelCapacity("m", "M", -5, null, Double.NaN).prompt()).isEmpty();
        assertThat(new ModelCapacity("m", "M", 10_000_000, null, 0.0).prompt()).hasValue(10_000_000);
        assertThatThrownBy(() -> new ModelCapacity(" ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
