package dev.specwriter.application.consent;

import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConsentRepository;

/** Donne ou retire un consentement ; chaque changement est ajouté à l'historique (FR-024). */
public class UpdateConsent {

    private final ConsentRepository consents;
    private final ClockPort clock;

    public UpdateConsent(ConsentRepository consents, ClockPort clock) {
        this.consents = consents;
        this.clock = clock;
    }

    public void run(ConsentService service, boolean granted) {
        consents.record(new ConsentRecord(service, granted, clock.now()));
    }
}
