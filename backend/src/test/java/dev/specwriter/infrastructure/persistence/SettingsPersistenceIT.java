package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.port.ConsentRepository;
import dev.specwriter.domain.port.SettingsRepository;
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
        consents.record(new ConsentRecord(ConsentService.JIRA, true, Instant.parse("2026-10-30T10:00:00Z")));
        consents.record(new ConsentRecord(ConsentService.JIRA, false, Instant.parse("2026-10-30T11:00:00Z")));
        assertThat(consents.latest(ConsentService.JIRA)).get().extracting(ConsentRecord::granted).isEqualTo(false);
    }
}
