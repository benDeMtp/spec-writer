package fr.kawamind.domain.port;

import fr.kawamind.domain.knowledge.ReliabilitySettings;
import java.util.UUID;

/** Niveaux de fiabilité des sortes de source d'un projet (016, R6) ; seuls les écarts au défaut. */
public interface ReliabilityRepository {

    ReliabilitySettings find(UUID projectId);

    void save(ReliabilitySettings settings);
}
