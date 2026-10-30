package dev.specwriter.support;

import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InMemorySpecifications implements SpecificationRepository {
    private final List<SpecificationVersion> versions = new ArrayList<>();

    @Override
    public void save(SpecificationVersion version) {
        versions.add(version);
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
    public int lastNumber(UUID sessionId) {
        return list(sessionId).stream().mapToInt(SpecificationVersion::number).max().orElse(0);
    }
}
