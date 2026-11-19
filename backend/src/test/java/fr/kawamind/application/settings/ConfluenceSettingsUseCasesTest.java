package fr.kawamind.application.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ConfluenceClient.Failure;
import fr.kawamind.domain.port.SecretStore;
import fr.kawamind.domain.settings.ConfluenceSettings;
import fr.kawamind.domain.settings.ConfluenceTestResult;
import fr.kawamind.support.FakeConfluenceClient;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryConfluenceSettings;
import fr.kawamind.support.InMemorySecrets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** T032 (016) : enregistrement, retrait et test de connexion du serveur Confluence. */
class ConfluenceSettingsUseCasesTest {

    static final String SERVER = FakeConfluenceClient.SERVER;
    static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    final InMemoryConfluenceSettings settings = new InMemoryConfluenceSettings();
    final InMemorySecrets secrets = new InMemorySecrets();
    final FakeConfluenceClient wiki = new FakeConfluenceClient();
    final SaveConfluenceSettings save = new SaveConfluenceSettings(settings, secrets);
    final TestConfluenceConnection test = new TestConfluenceConnection(wiki, settings, secrets, new FixedClock(NOW));

    @Test
    void leJetonVaAuTrousseauEtUnJetonAbsentEstGarde() {
        assertThat(save.tokenSet()).isFalse();
        ConfluenceSettings saved = save.run(new SaveConfluenceSettings.Command(SERVER + "/", " PAT ", null, false));

        assertThat(saved.serverUrl()).isEqualTo(SERVER);
        assertThat(settings.find()).contains(saved);
        assertThat(secrets.get(SecretStore.CONFLUENCE_PAT)).contains("PAT");
        assertThat(save.tokenSet()).isTrue();

        save.run(new SaveConfluenceSettings.Command(SERVER, null, null, false));
        assertThat(secrets.get(SecretStore.CONFLUENCE_PAT)).contains("PAT");
        assertThat(new SaveConfluenceSettings.Command(SERVER, "secret", null, false).toString())
                .doesNotContain("secret").contains("***");
    }

    @Test
    void leRetraitEnleveLeServeurEtLeJeton() {
        save.run(new SaveConfluenceSettings.Command(SERVER, "PAT", null, false));

        new DeleteConfluenceSettings(settings, secrets).run();

        assertThat(settings.find()).isEmpty();
        assertThat(secrets.get(SecretStore.CONFLUENCE_PAT)).isEmpty();
    }

    @Test
    void connexionReussieAvecLeCompteEtLaVersion() {
        save.run(new SaveConfluenceSettings.Command(SERVER, "PAT", null, false));

        TestConfluenceConnection.Outcome outcome = test.run();

        assertThat(outcome.result()).isEqualTo(ConfluenceTestResult.OK);
        assertThat(outcome.account()).isEqualTo("Ada");
        assertThat(outcome.serverVersion()).isEqualTo("8.5.4");
        assertThat(outcome.message()).isNotBlank();
        assertThat(settings.find().orElseThrow().lastTest())
                .isEqualTo(new ConfluenceSettings.LastTest(NOW, ConfluenceTestResult.OK));
    }

    @ParameterizedTest
    @CsvSource({"UNAUTHORIZED,UNAUTHORIZED", "FORBIDDEN,UNAUTHORIZED", "TLS_ERROR,TLS_ERROR",
        "NOT_CONFLUENCE,NOT_CONFLUENCE", "NOT_FOUND,NOT_CONFLUENCE", "REJECTED,NOT_CONFLUENCE",
        "UNREACHABLE,UNREACHABLE", "RATE_LIMITED,UNREACHABLE"})
    void chaqueEchecDitQuoiFaire(Failure failure, ConfluenceTestResult expected) {
        save.run(new SaveConfluenceSettings.Command(SERVER, "PAT", null, false));
        wiki.down = failure;

        TestConfluenceConnection.Outcome outcome = test.run();

        assertThat(outcome.result()).isEqualTo(expected);
        assertThat(outcome.account()).isNull();
        assertThat(settings.find().orElseThrow().lastTest().result()).isEqualTo(expected);
    }

    @Test
    void sansJetonLaConnexionEstRefuseeSansAppel() {
        settings.save(ConfluenceSettings.create(SERVER, null, false, fr.kawamind.domain.settings.SecretPersistence.KEYCHAIN));

        assertThat(test.run().result()).isEqualTo(ConfluenceTestResult.UNAUTHORIZED);
        assertThat(wiki.calls).isEmpty();
    }

    @Test
    void sansServeurLeTestRenvoieVersConfiguration() {
        assertThatThrownBy(test::run).isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.CONFLUENCE_NOT_CONFIGURED));
    }
}
