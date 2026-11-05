package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.port.ConsentRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.EmbeddingSettings;
import dev.specwriter.domain.settings.CopilotSettings;
import dev.specwriter.domain.settings.JiraSettings;
import dev.specwriter.domain.settings.JiraTestResult;
import dev.specwriter.domain.settings.SecretPersistence;
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
