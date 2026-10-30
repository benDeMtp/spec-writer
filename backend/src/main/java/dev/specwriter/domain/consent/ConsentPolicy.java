package dev.specwriter.domain.consent;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ConsentRepository;

/** Aucune donnée n'est envoyée à un service tiers sans accord en vigueur (FR-024, principe V). */
public final class ConsentPolicy {

    private final ConsentRepository consents;

    public ConsentPolicy(ConsentRepository consents) {
        this.consents = consents;
    }

    public boolean isGranted(ConsentService service) {
        return consents.latest(service).map(ConsentRecord::granted).orElse(false);
    }

    public void requireGranted(ConsentService service) {
        if (!isGranted(service)) {
            throw new DomainException(ErrorCode.CONSENT_REQUIRED, service == ConsentService.JIRA
                    ? "Votre accord est nécessaire avant d'envoyer des données à Jira."
                    : "Votre accord est nécessaire avant d'envoyer votre besoin à GitHub Copilot.");
        }
    }
}
