package fr.kawamind.support;

import fr.kawamind.domain.onboarding.Onboarding;
import fr.kawamind.domain.port.OnboardingRepository;

public class InMemoryOnboarding implements OnboardingRepository {
    private Onboarding onboarding = Onboarding.initial();

    @Override
    public Onboarding load() {
        return onboarding;
    }

    @Override
    public void save(Onboarding onboarding) {
        this.onboarding = onboarding;
    }
}
