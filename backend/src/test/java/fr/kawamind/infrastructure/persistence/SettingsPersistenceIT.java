package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.port.ConsentRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SettingsRepository;
import fr.kawamind.domain.settings.EmbeddingSettings;
import fr.kawamind.domain.settings.CopilotSettings;
import fr.kawamind.domain.settings.JiraSettings;
import fr.kawamind.domain.settings.JiraTestResult;
import fr.kawamind.domain.settings.ModelCapacity;
import fr.kawamind.domain.settings.SecretPersistence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SettingsPersistenceIT {

    @Inject
    SettingsRepository settings;

    @Inject
    ConsentRepository consents;

    @Inject
    ProjectRepository projects;

    @Test
    void reglagesDEmbeddingGardesQuandLeProjetCourantChange() {
        EmbeddingSettings camembert = new EmbeddingSettings("/modeles/camembert", EmbeddingSettings.Pooling.CLS, "", "");
        settings.saveEmbedding(camembert);
        try {
            projects.saveCurrentProjectId(projects.currentProjectId().orElse(null));
            assertThat(settings.embedding()).isEqualTo(camembert);
        } finally {
            settings.saveEmbedding(EmbeddingSettings.DEFAULT);
        }
        assertThat(settings.embedding()).isEqualTo(EmbeddingSettings.DEFAULT);
    }

    @Test
    void reglagesCopilotEtJira() {
        CopilotSettings c = CopilotSettings.initial().connected("ada", List.of("a", "b")).withModel("b");
        settings.saveCopilot(c);
        assertThat(settings.copilot()).isEqualTo(c);
        assertThat(settings.copilot().capacities()).isEmpty();

        // 008 : capacités relues à l'identique, y compris les valeurs inconnues
        CopilotSettings sized = CopilotSettings.initial().connected("ada", List.of("a", "b"), List.of(
                new ModelCapacity("a", "Modèle A", 128_000, 200_000, 1.0), new ModelCapacity("b", null, null, null, null)))
                .withModel("a");
        settings.saveCopilot(sized);
        assertThat(settings.copilot()).isEqualTo(sized);
        assertThat(settings.copilot().capacity()).hasValueSatisfying(m -> assertThat(m.promptTokens()).isEqualTo(128_000));

        JiraSettings j = JiraSettings.create("https://jira.example.com", "PROJ", null, false, SecretPersistence.KEYCHAIN)
                .withTest(Instant.parse("2026-10-30T10:00:00Z"), JiraTestResult.OK, "9.12.3");
        settings.saveJira(j);
        assertThat(settings.jira()).contains(j);
        settings.deleteJira();
        assertThat(settings.jira()).isEmpty();
    }

    @Test
    void dernierConsentementFaitFoi() {
        // la base est partagée avec les IT web, qui enregistrent leurs consentements à l'heure courante
        Instant now = Instant.now();
        consents.record(new ConsentRecord(ConsentService.JIRA, true, now.minusSeconds(1)));
        consents.record(new ConsentRecord(ConsentService.JIRA, false, now));
        assertThat(consents.latest(ConsentService.JIRA)).get().extracting(ConsentRecord::granted).isEqualTo(false);
    }
}
