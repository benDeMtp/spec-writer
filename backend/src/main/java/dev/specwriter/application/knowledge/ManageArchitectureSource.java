package dev.specwriter.application.knowledge;

import dev.specwriter.application.knowledge.SynchronizeKnowledge.Mode;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSource;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.KnowledgeSourceRepository;
import dev.specwriter.domain.port.ProjectRepository;
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
