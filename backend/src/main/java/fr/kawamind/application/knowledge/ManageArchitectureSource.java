package fr.kawamind.application.knowledge;

import fr.kawamind.application.knowledge.SynchronizeKnowledge.Mode;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.ArchitectureModel;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.knowledge.KnowledgeSource;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.KnowledgeSourceRepository;
import fr.kawamind.domain.port.ProjectRepository;
import java.util.Set;
import java.util.UUID;

/**
 * Workspace Structurizr d'un projet (FR-038) : un chemin absolu vers un {@code workspace.dsl}
 * lisible, et l'identifiant du projet dans la propriété {@code ownership}. L'enregistrement lance
 * une indexation complète, sans l'attendre.
 */
public class ManageArchitectureSource {

    private final ProjectRepository projects;
    private final KnowledgeSourceRepository sources;
    private final KnowledgeIndex index;
    private final Indexations indexations;

    public ManageArchitectureSource(ProjectRepository projects, KnowledgeSourceRepository sources, KnowledgeIndex index,
            Indexations indexations) {
        this.projects = projects;
        this.sources = sources;
        this.index = index;
        this.indexations = indexations;
    }

    public KnowledgeSource get(UUID projectId) {
        requireProject(projectId);
        return sources.find(projectId);
    }

    public KnowledgeSource set(UUID projectId, String workspace, String ownership) {
        requireProject(projectId);
        KnowledgeSource source = sources.find(projectId)
                .withArchitecture(KnowledgeSource.validWorkspace(workspace), KnowledgeSource.validOwnership(ownership));
        sources.save(source);
        indexations.trigger(projectId, Mode.FULL);
        return source;
    }

    /** Retire le workspace et le modèle d'architecture de l'index. */
    public void clear(UUID projectId) {
        requireProject(projectId);
        sources.save(sources.find(projectId).withArchitecture(null, null));
        index.retain(projectId, KnowledgeKind.ARCH_ELEMENT, Set.of());
        index.replaceArchitecture(projectId, ArchitectureModel.EMPTY);
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
