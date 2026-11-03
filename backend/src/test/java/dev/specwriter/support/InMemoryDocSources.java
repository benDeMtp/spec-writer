package dev.specwriter.support;

import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.project.DocSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryDocSources implements DocSourceRepository {
    private final Map<UUID, DocSource> sources = new LinkedHashMap<>();

    @Override
    public void save(DocSource source) {
        sources.put(source.id(), source);
    }

    @Override
    public Optional<DocSource> find(UUID id) {
        return Optional.ofNullable(sources.get(id));
    }

    @Override
    public List<DocSource> list(UUID projectId) {
        return sources.values().stream().filter(s -> s.projectId().equals(projectId)).toList();
    }

    @Override
    public void delete(UUID id) {
        sources.remove(id);
    }
}
