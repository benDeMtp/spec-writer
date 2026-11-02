package dev.specwriter.domain.port;

import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import java.util.Optional;

public interface ConsentRepository {

    void record(ConsentRecord record);

    /** Dernier enregistrement pour ce service. */
    Optional<ConsentRecord> latest(ConsentService service);
}
