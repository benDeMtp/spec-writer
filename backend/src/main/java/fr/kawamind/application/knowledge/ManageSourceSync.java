package fr.kawamind.application.knowledge;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.sync.SourceSync;
import fr.kawamind.domain.sync.SourceSyncResult;
import fr.kawamind.domain.sync.SyncInterval;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Délai et dernier bilan de chaque source d'un projet (017, US2, FR-007, FR-008). */
public class ManageSourceSync {

    /** Ce que voit l'utilisateur d'une source. */
    public record State(String sourceKey, IndexableSource.Kind kind, String label, SyncInterval interval,
            SourceSyncResult last, Instant lastSuccessAt, Instant nextRunAt) {
    }

    private final SourceSyncRepository syncs;
    private final Indexations indexations;
    private final Duration minute;

    public ManageSourceSync(SourceSyncRepository syncs, Indexations indexations, Duration minute) {
        this.syncs = syncs;
        this.indexations = indexations;
        this.minute = minute;
    }

    /** Une entrée par source indexable, dans l'ordre de la réindexation par source. */
    public List<State> list(UUID projectId) {
        return indexations.sources(projectId).stream().map(s -> state(projectId, s)).toList();
    }

    public State setInterval(UUID projectId, String sourceKey, Integer minutes) {
        IndexableSource source = indexations.sources(projectId).stream().filter(s -> s.id().equals(sourceKey))
                .findFirst().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette source n'existe plus."));
        syncs.setInterval(projectId, sourceKey, SyncInterval.fromMinutes(minutes));
        return state(projectId, source);
    }

    private State state(UUID projectId, IndexableSource source) {
        SourceSync s = syncs.find(projectId, source.id()).orElse(SourceSync.empty(projectId, source.id()));
        return new State(source.id(), source.kind(), source.label(), s.interval(), s.last(), s.lastSuccessAt(),
                s.last() == null ? null : s.dueAt(minute).orElse(null));
    }
}
