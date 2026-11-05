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

    void deleteAll(UUID sessionId);

    /** Supprime une version ; les numéros des autres ne changent pas (FR-014). */
    void delete(UUID sessionId, int number);

    /** 0 si la session n'a pas encore de version. */
    int lastNumber(UUID sessionId);

    /**
     * Numéro de la prochaine version : jamais un numéro déjà attribué, même supprimé (research.md R8).
     */
    int nextNumber(UUID sessionId);
}
