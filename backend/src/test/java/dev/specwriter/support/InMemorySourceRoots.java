package dev.specwriter.support;

import dev.specwriter.domain.port.SourceRootRepository;
import dev.specwriter.domain.sources.SourceRoot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemorySourceRoots implements SourceRootRepository {
    private final Map<UUID, SourceRoot> roots = new LinkedHashMap<>();

    @Override
    public SourceRoot save(SourceRoot root) {
        roots.put(root.id(), root);
        return root;
    }

    @Override
    public Optional<SourceRoot> find(UUID projectId, UUID rootId) {
        return Optional.ofNullable(roots.get(rootId)).filter(r -> r.projectId().equals(projectId));
    }

    @Override
    public List<SourceRoot> list(UUID projectId) {
        return roots.values().stream().filter(r -> r.projectId().equals(projectId)).toList();
    }

    @Override
    public void delete(UUID projectId, UUID rootId) {
        find(projectId, rootId).ifPresent(r -> roots.remove(rootId));
    }
}
