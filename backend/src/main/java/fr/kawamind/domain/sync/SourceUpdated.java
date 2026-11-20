package fr.kawamind.domain.sync;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Une source a changé à sa dernière synchronisation (017, US3, FR-014) : quelle source, de quel
 * projet, et combien d'éléments. Jamais le contenu des éléments.
 *
 * @param sourceKind sorte de la source : {@code JIRA}, {@code DOCUMENTATION} ou {@code ARCHITECTURE}
 */
public record SourceUpdated(UUID id, UUID projectId, String projectName, String sourceKey, String sourceKind,
        String sourceLabel, SourceChange change, Instant at) {

    public SourceUpdated {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(at, "at");
    }
}
