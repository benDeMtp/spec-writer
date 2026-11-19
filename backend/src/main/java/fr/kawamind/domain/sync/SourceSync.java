package fr.kawamind.domain.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Délai et dernier bilan d'une source d'un projet (017, data-model.md). La clé est celle de la
 * réindexation par source : {@code JIRA}, {@code ARCHITECTURE} ou l'identifiant d'une source de
 * documentation. L'échéance part de la dernière tentative, réussie ou non (FR-012).
 */
public record SourceSync(UUID projectId, String sourceKey, SyncInterval interval, SourceSyncResult last,
        Instant lastSuccessAt) {

    public SourceSync {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(interval, "interval");
    }

    public static SourceSync empty(UUID projectId, String sourceKey) {
        return new SourceSync(projectId, sourceKey, SyncInterval.OFF, null, null);
    }

    public SourceSync withInterval(SyncInterval newInterval) {
        return new SourceSync(projectId, sourceKey, newInterval, last, lastSuccessAt);
    }

    public SourceSync recorded(SourceSyncResult result) {
        return new SourceSync(projectId, sourceKey, interval, result, result.succeeded() ? result.at() : lastSuccessAt);
    }

    /** Prochaine synchronisation ; une source jamais synchronisée est échue dès qu'un délai lui est donné. */
    public Optional<Instant> dueAt(Duration minute) {
        if (interval == SyncInterval.OFF) {
            return Optional.empty();
        }
        return Optional.of(last == null ? Instant.EPOCH : last.at().plus(interval.duration(minute)));
    }

    public boolean isDue(Instant now, Duration minute) {
        return dueAt(minute).map(d -> !d.isAfter(now)).orElse(false);
    }
}
