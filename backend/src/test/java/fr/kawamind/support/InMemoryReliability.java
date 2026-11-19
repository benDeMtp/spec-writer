package fr.kawamind.support;

import fr.kawamind.domain.knowledge.ReliabilitySettings;
import fr.kawamind.domain.port.ReliabilityRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Niveaux de fiabilité en mémoire (016). */
public class InMemoryReliability implements ReliabilityRepository {

    public final Map<UUID, ReliabilitySettings> settings = new HashMap<>();

    @Override
    public ReliabilitySettings find(UUID projectId) {
        return settings.getOrDefault(projectId, ReliabilitySettings.defaults(projectId));
    }

    @Override
    public void save(ReliabilitySettings s) {
        settings.put(s.projectId(), s);
    }
}
