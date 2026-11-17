package fr.kawamind.domain.port;

import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import java.util.Optional;

public interface ConsentRepository {

    void record(ConsentRecord record);

    /** Dernier enregistrement pour ce service. */
    Optional<ConsentRecord> latest(ConsentService service);
}
