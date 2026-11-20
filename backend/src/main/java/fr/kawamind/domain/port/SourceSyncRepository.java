package fr.kawamind.domain.port;

import fr.kawamind.domain.sync.SourceSync;
import fr.kawamind.domain.sync.SourceSyncResult;
import fr.kawamind.domain.sync.SyncInterval;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Délais et bilans des sources (017, data-model.md). Une source sans ligne est désactivée et jamais synchronisée. */
public interface SourceSyncRepository {

    Optional<SourceSync> find(UUID projectId, String sourceKey);

    List<SourceSync> of(UUID projectId);

    /** Sources de tous les projets dont le délai est actif. */
    List<SourceSync> scheduled();

    void setInterval(UUID projectId, String sourceKey, SyncInterval interval);

    void record(UUID projectId, String sourceKey, SourceSyncResult result);
}
