package dev.specwriter.application.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.consent.UpdateConsent;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.GitHubAuthenticator;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.settings.CopilotSettings.SubscriptionState;
import dev.specwriter.domain.settings.SecretPersistence;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemorySecrets;
import dev.specwriter.support.InMemorySettings;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsUseCasesTest {

    static class StubAuthenticator implements GitHubAuthenticator {
        ConnectionStatus next = new ConnectionStatus(State.PENDING, null);
        List<ModelOption> models = List.of(new ModelOption("gpt-6-luna", "GPT 6 Luna", true),
                new ModelOption("claude-sonnet-5.5", "Claude Sonnet 5.5", true),
                new ModelOption("bloqué", "Bloqué", false));
        boolean disconnected;

        @Override
        public ConnectionStart startConnection() {
            return new ConnectionStart("f1", "https://github.com/login/oauth/authorize", List.of("read:user"),
                    Instant.EPOCH);
        }

        @Override
        public ConnectionStatus poll(String flowId) {
            return next;
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public Optional<String> connectedLogin() {
            return Optional.empty();
        }

        @Override
        public List<ModelOption> listModels() {
            return models;
        }
    }

    final InMemorySettings settings = new InMemorySettings();
    final InMemorySecrets secrets = new InMemorySecrets();
    final StubAuthenticator auth = new StubAuthenticator();

    @Test
    void enregistrementJiraAvecJetonAuTrousseau() {
        var saved = new SaveJiraSettings(settings, secrets).run(
                new SaveJiraSettings.Command("https://jira.example.fr/", "PROJ", " PAT-1 ", null, false));
        assertThat(saved.serverUrl()).isEqualTo("https://jira.example.fr");
        assertThat(secrets.get(SecretStore.JIRA_PAT)).contains("PAT-1");
        assertThat(settings.jira()).contains(saved);
    }

    @Test
    void sansJetonLeJetonExistantEstConserve() {
        secrets.put(SecretStore.JIRA_PAT, "ANCIEN");
        new SaveJiraSettings(settings, secrets).run(
                new SaveJiraSettings.Command("https://jira.example.fr", "PROJ", null, null, false));
        assertThat(secrets.get(SecretStore.JIRA_PAT)).contains("ANCIEN");
    }

    @Test
    void trousseauIndisponibleSignaleEnMemoire() {
        secrets.persistence = SecretStore.Persistence.MEMORY_ONLY;
        var saved = new SaveJiraSettings(settings, secrets).run(
                new SaveJiraSettings.Command("https://jira.example.fr", "PROJ", "PAT", null, false));
        assertThat(saved.secretPersistence()).isEqualTo(SecretPersistence.MEMORY_ONLY);
    }

    @Test
    void leJetonNApparaitPasDansLaCommande() {
        assertThat(new SaveJiraSettings.Command("https://j", "P", "SECRET-PAT", null, false).toString())
                .doesNotContain("SECRET-PAT");
    }

    @Test
    void suppressionJira() {
        new SaveJiraSettings(settings, secrets).run(
                new SaveJiraSettings.Command("https://jira.example.fr", "PROJ", "PAT", null, false));
        new DeleteJiraSettings(settings, secrets).run();
        assertThat(settings.jira()).isEmpty();
        assertThat(secrets.get(SecretStore.JIRA_PAT)).isEmpty();
    }

    @Test
    void connexionEnregistreLesModelesAutorisesEtLeModeleParDefaut() {
        assertThat(new StartCopilotConnection(auth).run().flowId()).isEqualTo("f1");
        auth.next = new GitHubAuthenticator.ConnectionStatus(GitHubAuthenticator.State.CONNECTED, "ada");
        var status = new PollCopilotConnection(auth, settings).run("f1");
        assertThat(status.state()).isEqualTo(GitHubAuthenticator.State.CONNECTED);
        assertThat(settings.copilot().connectedLogin()).isEqualTo("ada");
        assertThat(settings.copilot().availableModels()).containsExactly("gpt-6-luna", "claude-sonnet-5.5");
        assertThat(settings.copilot().model()).isEqualTo("claude-sonnet-5.5");
    }

    @Test
    void connecteSansModeleDonneNoCopilot() {
        auth.models = List.of();
        auth.next = new GitHubAuthenticator.ConnectionStatus(GitHubAuthenticator.State.CONNECTED, "ada");
        assertThat(new PollCopilotConnection(auth, settings).run("f1").state())
                .isEqualTo(GitHubAuthenticator.State.NO_COPILOT);
        assertThat(settings.copilot().subscriptionState()).isEqualTo(SubscriptionState.NO_COPILOT);
    }

    @Test
    void enAttenteRienNEstEnregistre() {
        new PollCopilotConnection(auth, settings).run("f1");
        assertThat(settings.copilot().connectedLogin()).isNull();
    }

    @Test
    void choixDuModele() {
        auth.next = new GitHubAuthenticator.ConnectionStatus(GitHubAuthenticator.State.CONNECTED, "ada");
        new PollCopilotConnection(auth, settings).run("f1");
        assertThat(new SelectModel(settings).run("gpt-6-luna").model()).isEqualTo("gpt-6-luna");
        assertThatThrownBy(() -> new SelectModel(settings).run("bloqué"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MODEL_UNAVAILABLE));
    }

    @Test
    void deconnexionSupprimeLesJetons() {
        secrets.put(SecretStore.COPILOT_ACCESS, "x");
        auth.next = new GitHubAuthenticator.ConnectionStatus(GitHubAuthenticator.State.CONNECTED, "ada");
        new PollCopilotConnection(auth, settings).run("f1");
        new DisconnectCopilot(auth, settings, secrets).run();
        assertThat(auth.disconnected).isTrue();
        assertThat(secrets.get(SecretStore.COPILOT_ACCESS)).isEmpty();
        assertThat(settings.copilot().connectedLogin()).isNull();
        assertThat(settings.copilot().model()).isNull();
    }

    @Test
    void consentementHistorise() {
        InMemoryConsents consents = new InMemoryConsents();
        UpdateConsent update = new UpdateConsent(consents, new FixedClock());
        update.run(ConsentService.JIRA, true);
        update.run(ConsentService.JIRA, false);
        assertThat(consents.history()).hasSize(2);
        assertThat(consents.latest(ConsentService.JIRA).orElseThrow().granted()).isFalse();
    }
}
