package dev.specwriter.support;

import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemorySpecifications implements SpecificationRepository {
    private final List<SpecificationVersion> versions = new ArrayList<>();
    private final Map<UUID, Integer> next = new HashMap<>();

    @Override
    public void save(SpecificationVersion version) {
        versions.add(version);
        next.merge(version.sessionId(), version.number() + 1, Math::max);
    }

    @Override
    public Optional<SpecificationVersion> find(UUID sessionId, int number) {
        return list(sessionId).stream().filter(v -> v.number() == number).findFirst();
    }

    @Override
    public Optional<SpecificationVersion> findByFingerprint(UUID sessionId, String fingerprint) {
        return list(sessionId).stream().filter(v -> v.inputFingerprint().equals(fingerprint)).findFirst();
    }

    @Override
    public List<SpecificationVersion> list(UUID sessionId) {
        return versions.stream().filter(v -> v.sessionId().equals(sessionId)).toList();
    }

    @Override
    public void deleteAll(UUID sessionId) {
        versions.removeIf(v -> v.sessionId().equals(sessionId));
    }

    @Override
    public void delete(UUID sessionId, int number) {
        versions.removeIf(v -> v.sessionId().equals(sessionId) && v.number() == number);
    }

    @Override
    public int nextNumber(UUID sessionId) {
        return Math.max(next.getOrDefault(sessionId, 1), lastNumber(sessionId) + 1);
    }

    @Override
    public int lastNumber(UUID sessionId) {
        return list(sessionId).stream().mapToInt(SpecificationVersion::number).max().orElse(0);
    }
}
