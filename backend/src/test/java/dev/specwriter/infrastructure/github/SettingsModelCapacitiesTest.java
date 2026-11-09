package dev.specwriter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.settings.CopilotSettings;
import dev.specwriter.domain.settings.ModelCapacity;
import dev.specwriter.support.InMemorySettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsModelCapacitiesTest {

    final InMemorySettings settings = new InMemorySettings();
    final SettingsModelCapacities capacities = new SettingsModelCapacities(settings);
    final List<ModelCapacity> known = List.of(new ModelCapacity("grand", "Grand", 128_000, 200_000, 1.0),
            new ModelCapacity("inconnu", null, null, null, null));

    @Test
    void limiteDuModeleChoisi() {
        settings.saveCopilot(CopilotSettings.initial().connected("ada", List.of("grand", "inconnu", "absent"), known)
                .withModel("grand"));
        assertThat(capacities.promptTokens()).hasValue(128_000);
        assertThat(capacities.budget().total()).isEqualTo(162_000);
        assertThat(capacities.all()).isEqualTo(known);
    }

    @Test
    void modeleSansLimiteAbsentOuNonChoisiDonneLeBudgetDAujourdhui() {
        CopilotSettings c = CopilotSettings.initial().connected("ada", List.of("grand", "inconnu", "absent"), known);
        settings.saveCopilot(c.withModel("inconnu"));
        assertThat(capacities.promptTokens()).isEmpty();
        settings.saveCopilot(c.withModel("absent"));
        assertThat(capacities.budget()).isEqualTo(KnowledgeBudget.STANDARD);
        settings.saveCopilot(CopilotSettings.initial());
        assertThat(capacities.promptTokens()).isEmpty();
        assertThat(capacities.all()).isEmpty();
    }
}
