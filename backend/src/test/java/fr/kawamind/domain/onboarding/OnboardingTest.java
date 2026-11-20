package fr.kawamind.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** T008 (018) : progression de l'assistant de premier lancement (data-model.md). */
class OnboardingTest {

    static final Instant T0 = Instant.parse("2026-11-20T08:00:00Z");

    static Onboarding copilotFait() {
        return Onboarding.initial().markDone(OnboardingStep.COPILOT);
    }

    @Test
    void lesEtapesSeSuiventDansLOrdre() {
        assertThat(OnboardingStep.WELCOME.next()).isEqualTo(OnboardingStep.COPILOT);
        assertThat(OnboardingStep.COPILOT.next()).isEqualTo(OnboardingStep.JIRA);
        assertThat(OnboardingStep.JIRA.next()).isEqualTo(OnboardingStep.CONFLUENCE);
        assertThat(OnboardingStep.CONFLUENCE.next()).isEqualTo(OnboardingStep.PROJECT);
        assertThat(OnboardingStep.PROJECT.next()).isEqualTo(OnboardingStep.SOURCES);
        assertThat(OnboardingStep.SOURCES.next()).isEqualTo(OnboardingStep.INDEXATION);
        assertThat(OnboardingStep.INDEXATION.next()).isEqualTo(OnboardingStep.DONE);
        assertThat(OnboardingStep.DONE.next()).isEqualTo(OnboardingStep.DONE);
        assertThat(OnboardingStep.JIRA.previous()).isEqualTo(OnboardingStep.COPILOT);
        assertThat(OnboardingStep.WELCOME.previous()).isEqualTo(OnboardingStep.WELCOME);
    }

    @Test
    void seuleLaConnexionACopilotEstObligatoire() {
        assertThat(OnboardingStep.tracked()).filteredOn(OnboardingStep::required).containsExactly(OnboardingStep.COPILOT);
        assertThat(OnboardingStep.tracked()).containsExactly(OnboardingStep.COPILOT, OnboardingStep.JIRA,
                OnboardingStep.CONFLUENCE, OnboardingStep.PROJECT, OnboardingStep.SOURCES, OnboardingStep.INDEXATION);
    }

    @Test
    void uneInstallationViergeCommenceALAccueil() {
        Onboarding o = Onboarding.initial();
        assertThat(o.currentStep()).isEqualTo(OnboardingStep.WELCOME);
        assertThat(o.completed()).isFalse();
        assertThat(o.state(OnboardingStep.JIRA)).isEqualTo(StepState.TODO);
    }

    @Test
    void copilotNePeutPasEtrePasse() {
        assertThatThrownBy(() -> Onboarding.initial().skip(OnboardingStep.COPILOT))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ONBOARDING_COPILOT_REQUIRED));
    }

    @Test
    void onNeVaPasAuDelaDeCopilotTantQuIlNEstPasFait() {
        assertThatThrownBy(() -> Onboarding.initial().goTo(OnboardingStep.JIRA))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ONBOARDING_COPILOT_REQUIRED));
        assertThat(Onboarding.initial().goTo(OnboardingStep.COPILOT).currentStep()).isEqualTo(OnboardingStep.COPILOT);
    }

    @Test
    void passerUneEtapeNeContournePasCopilot() {
        assertThatThrownBy(() -> Onboarding.initial().skip(OnboardingStep.JIRA))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ONBOARDING_COPILOT_REQUIRED));
        assertThatThrownBy(() -> Onboarding.initial().markDone(OnboardingStep.PROJECT))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ONBOARDING_COPILOT_REQUIRED));
    }

    @Test
    void uneEtapeFaiteOuPasseeFaitAvancer() {
        Onboarding o = copilotFait();
        assertThat(o.state(OnboardingStep.COPILOT)).isEqualTo(StepState.DONE);
        assertThat(o.currentStep()).isEqualTo(OnboardingStep.JIRA);
        Onboarding passe = o.skip(OnboardingStep.JIRA);
        assertThat(passe.state(OnboardingStep.JIRA)).isEqualTo(StepState.SKIPPED);
        assertThat(passe.currentStep()).isEqualTo(OnboardingStep.CONFLUENCE);
    }

    @Test
    void revenirEnArriereGardeLesEtats() {
        Onboarding o = copilotFait().skip(OnboardingStep.JIRA).skip(OnboardingStep.CONFLUENCE);
        Onboarding retour = o.goTo(OnboardingStep.JIRA);
        assertThat(retour.currentStep()).isEqualTo(OnboardingStep.JIRA);
        assertThat(retour.state(OnboardingStep.JIRA)).isEqualTo(StepState.SKIPPED);
        assertThat(retour.state(OnboardingStep.CONFLUENCE)).isEqualTo(StepState.SKIPPED);
        assertThat(retour.markDone(OnboardingStep.JIRA).state(OnboardingStep.JIRA)).isEqualTo(StepState.DONE);
    }

    @Test
    void terminerExigeCopilotEtGardeLaPremiereDate() {
        assertThatThrownBy(() -> Onboarding.initial().complete(T0))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ONBOARDING_COPILOT_REQUIRED));
        Onboarding fini = copilotFait().complete(T0);
        assertThat(fini.completed()).isTrue();
        assertThat(fini.currentStep()).isEqualTo(OnboardingStep.DONE);
        assertThat(fini.complete(T0.plusSeconds(60)).completedAt()).isEqualTo(T0);
    }

    @Test
    void lesEtapesSansEtatNeSontPasSuivies() {
        assertThatThrownBy(() -> copilotFait().skip(OnboardingStep.WELCOME))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ONBOARDING_UNKNOWN_STEP));
        assertThatThrownBy(() -> copilotFait().markDone(OnboardingStep.DONE))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ONBOARDING_UNKNOWN_STEP));
    }
}
