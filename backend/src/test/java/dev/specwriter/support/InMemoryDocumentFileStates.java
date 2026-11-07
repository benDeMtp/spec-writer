package dev.specwriter.support;

import dev.specwriter.domain.port.DocumentFileStates;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryDocumentFileStates implements DocumentFileStates {

    private final Map<UUID, Map<String, FileState>> states = new LinkedHashMap<>();

    @Override
    public Map<String, FileState> of(UUID sourceId) {
        return Map.copyOf(states.getOrDefault(sourceId, Map.of()));
    }

    @Override
    public void replace(UUID sourceId, Map<String, FileState> next) {
        states.put(sourceId, new LinkedHashMap<>(next));
    }
}
