package dev.specwriter.domain.specification;

import dev.specwriter.domain.knowledge.KnowledgeSelection;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import java.time.Instant;
import java.util.UUID;

/**
 * Résultat d'une génération (FR-018). Une version n'est jamais modifiée. Elle garde l'empreinte et
 * la date de la fiche générale utilisée ({@code null} sans fiche, FR-013d), et la sélection de
 * connaissance transmise (research.md R24 de 002).
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
        Instant contextUpdatedAt,
        KnowledgeSelection knowledge) {

    public SpecificationVersion {
        contextHash = contextHash == null || contextHash.isEmpty() ? null : contextHash;
        contextUpdatedAt = contextHash == null ? null : contextUpdatedAt;
        knowledge = knowledge == null ? KnowledgeSelection.EMPTY : knowledge;
    }

    public SpecificationVersion(UUID id, UUID sessionId, int number, SpecificationContent content, String inputFingerprint,
            VersionOrigin origin, String markdown, Instant createdAt, String contextHash, Instant contextUpdatedAt) {
        this(id, sessionId, number, content, inputFingerprint, origin, markdown, createdAt, contextHash, contextUpdatedAt,
                KnowledgeSelection.EMPTY);
    }

    public SpecificationVersion(UUID id, UUID sessionId, int number, SpecificationContent content, String inputFingerprint,
            VersionOrigin origin, String markdown, Instant createdAt) {
        this(id, sessionId, number, content, inputFingerprint, origin, markdown, createdAt, null, null,
                KnowledgeSelection.EMPTY);
    }

    /** La même version, produite avec le contexte de projet donné. */
    public SpecificationVersion withContext(String hash, Instant updatedAt) {
        return new SpecificationVersion(id, sessionId, number, content, inputFingerprint, origin, markdown, createdAt, hash,
                updatedAt, knowledge);
    }

    /** La même version, produite avec cette connaissance : fiche générale (hash et date) et sélection (R24). */
    public SpecificationVersion withKnowledge(ProjectKnowledge used) {
        return new SpecificationVersion(id, sessionId, number, content, inputFingerprint, origin, markdown, createdAt,
                used.sheetHash(), used.sheetUpdatedAt(), used.selection());
    }
}
