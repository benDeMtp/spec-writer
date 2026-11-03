package dev.specwriter.domain.specification;

import java.time.Instant;
import java.util.UUID;

/**
 * Résultat d'une génération (FR-018). Une version n'est jamais modifiée. Elle garde l'empreinte et
 * la date du contexte de projet utilisé ({@code null} sans contexte, FR-013d).
 */
public record SpecificationVersion(
        UUID id,
        UUID sessionId,
        int number,
        SpecificationContent content,
        String inputFingerprint,
        VersionOrigin origin,
        String markdown,
        Instant createdAt,
        String contextHash,
        Instant contextUpdatedAt) {

    public SpecificationVersion {
        contextHash = contextHash == null || contextHash.isEmpty() ? null : contextHash;
        contextUpdatedAt = contextHash == null ? null : contextUpdatedAt;
    }

    public SpecificationVersion(UUID id, UUID sessionId, int number, SpecificationContent content, String inputFingerprint,
            VersionOrigin origin, String markdown, Instant createdAt) {
        this(id, sessionId, number, content, inputFingerprint, origin, markdown, createdAt, null, null);
    }

    /** La même version, produite avec le contexte de projet donné. */
    public SpecificationVersion withContext(String hash, Instant updatedAt) {
        return new SpecificationVersion(id, sessionId, number, content, inputFingerprint, origin, markdown, createdAt, hash,
                updatedAt);
    }
}
