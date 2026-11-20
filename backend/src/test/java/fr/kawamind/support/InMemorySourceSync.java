package fr.kawamind.support;

import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.sync.SourceSync;
import fr.kawamind.domain.sync.SourceSyncResult;
import fr.kawamind.domain.sync.SyncInterval;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Délais et bilans des sources en mémoire (017). */
public class InMemorySourceSync implements SourceSyncRepository {

    public final Map<String, SourceSync> rows = new LinkedHashMap<>();
    public final List<String> recorded = new ArrayList<>();

    private static String id(UUID projectId, String key) {
        return projectId + "/" + key;
    }

    @Override
    public synchronized Optional<SourceSync> find(UUID projectId, String sourceKey) {
        return Optional.ofNullable(rows.get(id(projectId, sourceKey)));
    }

    @Override
    public synchronized List<SourceSync> of(UUID projectId) {
        return rows.values().stream().filter(s -> s.projectId().equals(projectId)).toList();
    }

    @Override
    public synchronized List<SourceSync> scheduled() {
        return rows.values().stream().filter(s -> s.interval() != SyncInterval.OFF).toList();
    }

    @Override
    public synchronized void setInterval(UUID projectId, String sourceKey, SyncInterval interval) {
        rows.put(id(projectId, sourceKey), find(projectId, sourceKey).orElse(SourceSync.empty(projectId, sourceKey))
                .withInterval(interval));
    }

    @Override
    public synchronized void record(UUID projectId, String sourceKey, SourceSyncResult result) {
        rows.put(id(projectId, sourceKey), find(projectId, sourceKey).orElse(SourceSync.empty(projectId, sourceKey))
                .recorded(result));
        recorded.add(sourceKey + " " + result.outcome());
    }
}
