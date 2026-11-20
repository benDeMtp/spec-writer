package fr.kawamind.domain.onboarding;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Progression de l'assistant de premier lancement (018, data-model.md) : étape courante, état des
 * étapes faites ou passées, date de fin. Valeur immuable ; une seule pour l'installation.
 */
public record Onboarding(OnboardingStep currentStep, Map<OnboardingStep, StepState> states, Instant completedAt) {

    public Onboarding {
        EnumMap<OnboardingStep, StepState> copy = new EnumMap<>(OnboardingStep.class);
        states.forEach((step, state) -> {
            if (step.isTracked() && state != StepState.TODO) {
                copy.put(step, state);
            }
        });
        states = Map.copyOf(copy);
    }

    public static Onboarding initial() {
        return new Onboarding(OnboardingStep.WELCOME, Map.of(), null);
    }

    public boolean completed() {
        return completedAt != null;
    }

    public StepState state(OnboardingStep step) {
        return states.getOrDefault(step, StepState.TODO);
    }

    /** Étape faite : l'assistant passe à la suivante. */
    public Onboarding markDone(OnboardingStep step) {
        return with(tracked(step), StepState.DONE);
    }

    /** Étape passée (FR-003) : jamais la connexion à Copilot. */
    public Onboarding skip(OnboardingStep step) {
        if (tracked(step).required()) {
            throw new DomainException(ErrorCode.ONBOARDING_COPILOT_REQUIRED);
        }
        return with(step, StepState.SKIPPED);
    }

    /** Aller à une étape (FR-004) : en arrière toujours, au-delà de Copilot seulement s'il est fait. */
    public Onboarding goTo(OnboardingStep step) {
        if (step.ordinal() > OnboardingStep.COPILOT.ordinal() && !copilotDone()) {
            throw new DomainException(ErrorCode.ONBOARDING_COPILOT_REQUIRED);
        }
        return new Onboarding(step, states, completedAt);
    }

    /** Fin de l'assistant ; la première date est gardée. */
    public Onboarding complete(Instant at) {
        if (!copilotDone()) {
            throw new DomainException(ErrorCode.ONBOARDING_COPILOT_REQUIRED);
        }
        return new Onboarding(OnboardingStep.DONE, states, completedAt == null ? at : completedAt);
    }

    private boolean copilotDone() {
        return state(OnboardingStep.COPILOT) == StepState.DONE;
    }

    private Onboarding with(OnboardingStep step, StepState state) {
        if (step.ordinal() > OnboardingStep.COPILOT.ordinal() && !copilotDone()) {
            throw new DomainException(ErrorCode.ONBOARDING_COPILOT_REQUIRED);
        }
        EnumMap<OnboardingStep, StepState> next = new EnumMap<>(OnboardingStep.class);
        next.putAll(states);
        next.put(step, state);
        return new Onboarding(step.next(), next, completedAt);
    }

    private static OnboardingStep tracked(OnboardingStep step) {
        if (!step.isTracked()) {
            throw new DomainException(ErrorCode.ONBOARDING_UNKNOWN_STEP);
        }
        return step;
    }
}
