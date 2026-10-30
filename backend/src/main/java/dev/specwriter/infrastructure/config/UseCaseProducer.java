package dev.specwriter.infrastructure.config;

import dev.specwriter.application.consent.UpdateConsent;
import dev.specwriter.application.settings.DeleteJiraSettings;
import dev.specwriter.application.settings.DisconnectCopilot;
import dev.specwriter.application.settings.PollCopilotConnection;
import dev.specwriter.application.settings.SaveJiraSettings;
import dev.specwriter.application.settings.SelectModel;
import dev.specwriter.application.settings.StartCopilotConnection;
import dev.specwriter.application.settings.TestJiraConnection;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConsentRepository;
import dev.specwriter.domain.port.GitHubAuthenticator;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.domain.port.SettingsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Câblage des cas d'usage, qui restent sans dépendance au framework (plan.md, architecture hexagonale). */
@ApplicationScoped
public class UseCaseProducer {

    @Produces
    @ApplicationScoped
    ConsentPolicy consentPolicy(ConsentRepository consents) {
        return new ConsentPolicy(consents);
    }

    @Produces
    @ApplicationScoped
    UpdateConsent updateConsent(ConsentRepository consents, ClockPort clock) {
        return new UpdateConsent(consents, clock);
    }

    @Produces
    @ApplicationScoped
    TestJiraConnection testJiraConnection(IssueTracker tracker, SettingsRepository settings, SecretStore secrets,
            ConsentPolicy consent, ClockPort clock) {
        return new TestJiraConnection(tracker, settings, secrets, consent, clock);
    }

    @Produces
    @ApplicationScoped
    SaveJiraSettings saveJiraSettings(SettingsRepository settings, SecretStore secrets) {
        return new SaveJiraSettings(settings, secrets);
    }

    @Produces
    @ApplicationScoped
    DeleteJiraSettings deleteJiraSettings(SettingsRepository settings, SecretStore secrets) {
        return new DeleteJiraSettings(settings, secrets);
    }

    @Produces
    @ApplicationScoped
    StartCopilotConnection startCopilotConnection(GitHubAuthenticator authenticator) {
        return new StartCopilotConnection(authenticator);
    }

    @Produces
    @ApplicationScoped
    PollCopilotConnection pollCopilotConnection(GitHubAuthenticator authenticator, SettingsRepository settings) {
        return new PollCopilotConnection(authenticator, settings);
    }

    @Produces
    @ApplicationScoped
    DisconnectCopilot disconnectCopilot(GitHubAuthenticator authenticator, SettingsRepository settings,
            SecretStore secrets) {
        return new DisconnectCopilot(authenticator, settings, secrets);
    }

    @Produces
    @ApplicationScoped
    SelectModel selectModel(SettingsRepository settings) {
        return new SelectModel(settings);
    }
}
