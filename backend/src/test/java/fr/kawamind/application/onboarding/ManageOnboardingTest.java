package fr.kawamind.application.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.onboarding.OnboardingStep;
import fr.kawamind.domain.onboarding.StepState;
import fr.kawamind.domain.settings.CopilotSettings;
import fr.kawamind.domain.settings.SecretPersistence;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryConsents;
import fr.kawamind.support.InMemoryOnboarding;
import fr.kawamind.support.InMemorySettings;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T009 (018) : progression de l'assistant, obligation de Copilot vérifiée sur l'état réel. */
class ManageOnboardingTest {

    final InMemoryOnboarding repository = new InMemoryOnboarding();
    final InMemorySettings settings = new InMemorySettings();
    final InMemoryConsents consents = new InMemoryConsents();
    final FixedClock clock = new FixedClock(Instant.parse("2026-11-20T08:00:00Z"));

    ManageOnboarding manage(boolean autoOpen) {
        return new ManageOnboarding(repository, settings, consents, clock, autoOpen);
    }

    void copilot(String login, String model, boolean consent) {
        settings.saveCopilot(new CopilotSettings(login, CopilotSettings.SubscriptionState.ACTIVE,
                CopilotSettings.AuthMode.COPILOT_CLI, model, List.of("claude-sonnet-5.5"), List.of(), null,
                SecretPersistence.KEYCHAIN));
        consents.record(new ConsentRecord(ConsentService.COPILOT, consent, clock.now()));
    }

    static void refuse(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.ONBOARDING_COPILOT_REQUIRED));
    }

    @Test
    void ouvertureAutomatiqueTantQueNonTermineEtSiActive() {
        assertThat(manage(true).state().autoOpen()).isTrue();
        assertThat(manage(false).state().autoOpen()).isFalse();
        copilot("mona", "claude-sonnet-5.5", true);
        manage(true).markDone(OnboardingStep.COPILOT);
        assertThat(manage(true).complete().autoOpen()).isFalse();
    }

    @Test
    void copilotFaitSeulementConnecteAvecModeleEtAccord() {
        refuse(() -> manage(true).markDone(OnboardingStep.COPILOT));
        copilot("mona", null, true);
        refuse(() -> manage(true).markDone(OnboardingStep.COPILOT));
        copilot("mona", "claude-sonnet-5.5", false);
        refuse(() -> manage(true).markDone(OnboardingStep.COPILOT));
        copilot("mona", "claude-sonnet-5.5", true);
        var view = manage(true).markDone(OnboardingStep.COPILOT);
        assertThat(view.onboarding().state(OnboardingStep.COPILOT)).isEqualTo(StepState.DONE);
        assertThat(view.onboarding().currentStep()).isEqualTo(OnboardingStep.JIRA);
    }

    @Test
    void passerAvanceEtSEnregistre() {
        copilot("mona", "claude-sonnet-5.5", true);
        manage(true).markDone(OnboardingStep.COPILOT);
        manage(true).skip(OnboardingStep.JIRA);
        assertThat(repository.load().currentStep()).isEqualTo(OnboardingStep.CONFLUENCE);
        assertThat(repository.load().state(OnboardingStep.JIRA)).isEqualTo(StepState.SKIPPED);
        assertThat(manage(true).goTo(OnboardingStep.JIRA).onboarding().currentStep()).isEqualTo(OnboardingStep.JIRA);
    }

    @Test
    void terminerEstHorodateEtIdempotent() {
        refuse(() -> manage(true).complete());
        copilot("mona", "claude-sonnet-5.5", true);
        manage(true).markDone(OnboardingStep.COPILOT);
        assertThat(manage(true).complete().onboarding().completedAt()).isEqualTo(clock.now());
        clock.advance(java.time.Duration.ofMinutes(5));
        assertThat(manage(true).complete().onboarding().completedAt()).isEqualTo(Instant.parse("2026-11-20T08:00:00Z"));
    }
}
