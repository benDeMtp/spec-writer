package dev.specwriter.domain.port;

import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Versions de spécification, jamais modifiées (FR-018). */
public interface SpecificationRepository {

    void save(SpecificationVersion version);

    Optional<SpecificationVersion> find(UUID sessionId, int number);

    /** Version existante aux mêmes entrées (FR-014). */
    Optional<SpecificationVersion> findByFingerprint(UUID sessionId, String fingerprint);

    /** Par numéro croissant. */
    List<SpecificationVersion> list(UUID sessionId);

    /** 0 si la session n'a pas encore de version. */
    int lastNumber(UUID sessionId);
}
