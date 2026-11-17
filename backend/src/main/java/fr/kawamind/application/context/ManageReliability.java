package fr.kawamind.application.context;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.knowledge.ReliabilitySettings;
import fr.kawamind.domain.knowledge.SourceOrigin;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.ReliabilityRepository;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Niveaux de fiabilité des sortes de source d'un projet (016, FR-015, FR-016) : lus avec leurs
 * défauts, changés un à un. Un changement vaut à la prochaine recherche, sans réindexation (FR-022).
 */
public class ManageReliability {

    /** Niveau de chaque sorte réglable, et son défaut. */
    public record Levels(Map<SourceOrigin, Reliability> levels, Map<SourceOrigin, Reliability> defaults) {
    }

    private final ProjectRepository projects;
    private final ReliabilityRepository reliability;

    public ManageReliability(ProjectRepository projects, ReliabilityRepository reliability) {
        this.projects = projects;
        this.reliability = reliability;
    }

    public Levels get(UUID projectId) {
        requireProject(projectId);
        return levels(reliability.find(projectId));
    }

    /** Un niveau égal au défaut, ou {@code null}, efface le réglage. */
    public Levels set(UUID projectId, SourceOrigin origin, Reliability level) {
        requireProject(projectId);
        if (origin == null || !origin.adjustable()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Choisissez une sorte de source : Jira, Antora, SharePoint, Confluence, architecture ou"
                            + " spécifications.");
        }
        ReliabilitySettings changed = reliability.find(projectId).with(origin, level);
        reliability.save(changed);
        return levels(changed);
    }

    private static Levels levels(ReliabilitySettings settings) {
        Map<SourceOrigin, Reliability> levels = new EnumMap<>(SourceOrigin.class);
        Map<SourceOrigin, Reliability> defaults = new EnumMap<>(SourceOrigin.class);
        Arrays.stream(SourceOrigin.values()).filter(SourceOrigin::adjustable).forEach(o -> {
            levels.put(o, settings.level(o));
            defaults.put(o, ReliabilitySettings.defaultLevel(o));
        });
        return new Levels(levels, defaults);
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
