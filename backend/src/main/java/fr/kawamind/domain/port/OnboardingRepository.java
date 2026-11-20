package fr.kawamind.domain.port;

import fr.kawamind.domain.onboarding.Onboarding;

/** Progression de l'assistant de premier lancement, une seule pour l'installation (018, V29). */
public interface OnboardingRepository {

    Onboarding load();

    void save(Onboarding onboarding);
}
