package dev.specwriter.domain.specification;

import java.time.Instant;
import java.util.UUID;

/** Résultat d'une génération (FR-018). Une version n'est jamais modifiée. */
public record SpecificationVersion(
        UUID id,
        UUID sessionId,
        int number,
        SpecificationContent content,
        String inputFingerprint,
        VersionOrigin origin,
        String markdown,
        Instant createdAt) {
}
