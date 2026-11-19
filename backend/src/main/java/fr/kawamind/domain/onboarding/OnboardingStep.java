package fr.kawamind.domain.onboarding;

import java.util.Arrays;
import java.util.List;

/**
 * Étapes de l'assistant de premier lancement, dans leur ordre (018, FR-002). L'accueil et la fin
 * n'ont pas d'état ; seule la connexion à Copilot est obligatoire (FR-003).
 */
public enum OnboardingStep {
    WELCOME(false, false),
    COPILOT(true, true),
    JIRA(true, false),
    CONFLUENCE(true, false),
    PROJECT(true, false),
    SOURCES(true, false),
    INDEXATION(true, false),
    DONE(false, false);

    private final boolean tracked;
    private final boolean required;

    OnboardingStep(boolean tracked, boolean required) {
        this.tracked = tracked;
        this.required = required;
    }

    /** Étape qui a un état (faite, passée, à faire). */
    public boolean isTracked() {
        return tracked;
    }

    public boolean required() {
        return required;
    }

    public OnboardingStep next() {
        return this == DONE ? DONE : values()[ordinal() + 1];
    }

    public OnboardingStep previous() {
        return this == WELCOME ? WELCOME : values()[ordinal() - 1];
    }

    /** Étapes qui ont un état, dans l'ordre. */
    public static List<OnboardingStep> tracked() {
        return Arrays.stream(values()).filter(OnboardingStep::isTracked).toList();
    }
}
