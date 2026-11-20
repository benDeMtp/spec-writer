package fr.kawamind.application.onboarding;

import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.onboarding.Onboarding;
import fr.kawamind.domain.onboarding.OnboardingStep;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConsentRepository;
import fr.kawamind.domain.port.OnboardingRepository;
import fr.kawamind.domain.port.SettingsRepository;
import fr.kawamind.domain.settings.CopilotSettings;
import org.jboss.logging.Logger;

/**
 * Assistant de premier lancement (018, US1) : progression, étapes faites ou passées, fin. Seule la
 * connexion à Copilot est vérifiée ici (FR-003) ; les autres étapes passent par les API existantes,
 * qui font leurs propres contrôles. Le journal ne nomme que les étapes, jamais une valeur saisie.
 */
public class ManageOnboarding {

    private static final Logger LOG = Logger.getLogger(ManageOnboarding.class);

    /** Progression, et ouverture automatique à l'ouverture de l'outil (R5). */
    public record View(Onboarding onboarding, boolean autoOpen) {
    }

    private final OnboardingRepository repository;
    private final SettingsRepository settings;
    private final ConsentRepository consents;
    private final ClockPort clock;
    private final boolean autoOpen;

    public ManageOnboarding(OnboardingRepository repository, SettingsRepository settings, ConsentRepository consents,
            ClockPort clock, boolean autoOpen) {
        this.repository = repository;
        this.settings = settings;
        this.consents = consents;
        this.clock = clock;
        this.autoOpen = autoOpen;
    }

    public View state() {
        return view(repository.load());
    }

    public View markDone(OnboardingStep step) {
        if (step == OnboardingStep.COPILOT && !copilotReady()) {
            throw new DomainException(ErrorCode.ONBOARDING_COPILOT_REQUIRED);
        }
        return save(repository.load().markDone(step), "étape " + step.name() + " faite");
    }

    public View skip(OnboardingStep step) {
        return save(repository.load().skip(step), "étape " + step.name() + " passée");
    }

    public View goTo(OnboardingStep step) {
        return save(repository.load().goTo(step), null);
    }

    public View complete() {
        Onboarding current = repository.load();
        if (current.completed()) {
            return view(current);
        }
        return save(current.complete(clock.now()), "terminé");
    }

    /** Compte connecté avec Copilot, modèle choisi, accord d'envoi des données donné. */
    private boolean copilotReady() {
        CopilotSettings c = settings.copilot();
        return c.connectedLogin() != null && c.subscriptionState() != CopilotSettings.SubscriptionState.NO_COPILOT
                && c.model() != null && consents.latest(ConsentService.COPILOT).map(ConsentRecord::granted).orElse(false);
    }

    private View save(Onboarding next, String event) {
        repository.save(next);
        if (event != null) {
            LOG.infof("Assistant de premier lancement : %s", event);
        }
        return view(next);
    }

    private View view(Onboarding o) {
        return new View(o, autoOpen && !o.completed());
    }
}
