package dev.specwriter.support;

import dev.specwriter.domain.knowledge.KnowledgeSource;
import dev.specwriter.domain.port.KnowledgeSourceRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryKnowledgeSources implements KnowledgeSourceRepository {

    public final Map<UUID, KnowledgeSource> sources = new HashMap<>();

    @Override
    public KnowledgeSource find(UUID projectId) {
        return sources.getOrDefault(projectId, KnowledgeSource.empty(projectId));
    }

    @Override
    public void save(KnowledgeSource source) {
        sources.put(source.projectId(), source);
    }
}
