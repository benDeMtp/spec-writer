package fr.kawamind.application.sources;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SourceRootRepository;
import fr.kawamind.domain.port.SourceTree;
import fr.kawamind.domain.project.ExclusionPatterns;
import fr.kawamind.domain.sources.SourceRoot;
import java.util.List;
import java.util.UUID;

/**
 * Racines de sources d'un projet, 10 au plus (013, US1) : ni indexation, ni appel au modèle de
 * langage. Chemins et motifs ne sont jamais journalisés.
 */
public class ManageSourceRoots {

    private final ProjectRepository projects;
    private final SourceRootRepository roots;
    private final SourceTree tree;
    private final IdGenerator ids;
    private final ClockPort clock;

    public ManageSourceRoots(ProjectRepository projects, SourceRootRepository roots, SourceTree tree, IdGenerator ids,
            ClockPort clock) {
        this.projects = projects;
        this.roots = roots;
        this.tree = tree;
        this.ids = ids;
        this.clock = clock;
    }

    public List<SourceRoot> list(UUID projectId) {
        requireProject(projectId);
        return roots.list(projectId);
    }

    public SourceRoot add(UUID projectId, String location, List<String> exclusions) {
        requireProject(projectId);
        List<SourceRoot> existing = roots.list(projectId);
        SourceRoot.requireRoomFor(existing.size());
        SourceRoot root = SourceRoot.create(ids.next(), projectId, location, ExclusionPatterns.of(exclusions), clock.now());
        root.requireCompatibleWith(existing);
        return roots.save(root);
    }

    public SourceRoot setExclusions(UUID projectId, UUID rootId, List<String> exclusions) {
        return roots.save(owned(projectId, rootId).withExclusions(ExclusionPatterns.of(exclusions)));
    }

    /** Modules et décomptes d'un dossier, déclaré ou non, sans lire d'autre fichier que les descripteurs. */
    public SourceTree.Preview preview(UUID projectId, String location, List<String> exclusions) {
        requireProject(projectId);
        SourceRoot candidate = SourceRoot.create(ids.next(), projectId, location, ExclusionPatterns.of(exclusions),
                clock.now());
        SourceTree.Preview preview = tree.preview(candidate, SourceTree.Limits.DEFAULT);
        if (preview.problem() != null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Ce dossier ne peut pas être lu : " + location);
        }
        return preview;
    }

    /** Supprime la racine ; les analyses passées restent lisibles. */
    public void delete(UUID projectId, UUID rootId) {
        roots.delete(projectId, owned(projectId, rootId).id());
    }

    private SourceRoot owned(UUID projectId, UUID rootId) {
        requireProject(projectId);
        return roots.find(projectId, rootId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
