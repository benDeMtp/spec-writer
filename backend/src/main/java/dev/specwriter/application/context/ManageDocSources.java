package dev.specwriter.application.context;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.ExclusionPatterns;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sources de documentation d'un projet, 10 au plus (FR-013 de 002) : sites Antora, et dossiers de
 * documents avec leurs motifs d'exclusion et leur aperçu (006).
 */
public class ManageDocSources {

    /** Exemples montrés par l'aperçu, pour chaque catégorie (006, FR-010). */
    static final int EXAMPLES = 10;

    /** Effet des motifs sur un dossier, sans rien lire : noms et attributs seulement. */
    public record Preview(int retained, int excluded, int ignored, List<String> retainedExamples,
            List<String> excludedExamples, List<String> ignoredExamples, boolean limitReached) {
    }

    private final ProjectRepository projects;
    private final DocSourceRepository docSources;
    private final IdGenerator ids;
    private final KnowledgeIndex index;
    private final DocumentFolder folder;

    public ManageDocSources(ProjectRepository projects, DocSourceRepository docSources, IdGenerator ids,
            KnowledgeIndex index, DocumentFolder folder) {
        this.projects = projects;
        this.docSources = docSources;
        this.ids = ids;
        this.index = index;
        this.folder = folder;
    }

    public List<DocSource> list(UUID projectId) {
        requireProject(projectId);
        return docSources.list(projectId);
    }

    public DocSource add(UUID projectId, DocSource.Kind kind, String location) {
        return add(projectId, kind, location, List.of());
    }

    public DocSource add(UUID projectId, DocSource.Kind kind, String location, List<String> exclusions) {
        requireProject(projectId);
        DocSource.requireRoomFor(docSources.list(projectId).size());
        DocSource source = DocSource.create(ids.next(), projectId, kind, location, ExclusionPatterns.of(exclusions));
        docSources.save(source);
        return source;
    }

    /** Nouveaux motifs d'un dossier de documents, appliqués à sa prochaine indexation. */
    public DocSource setExclusions(UUID projectId, UUID sourceId, List<String> exclusions) {
        DocSource changed = owned(projectId, sourceId).withExclusions(ExclusionPatterns.of(exclusions));
        docSources.save(changed);
        return changed;
    }

    /** Aperçu des motifs sur un dossier, déclaré ou non, sans ouvrir aucun fichier. */
    public Preview preview(UUID projectId, String location, List<String> exclusions) {
        requireProject(projectId);
        DocSource candidate = DocSource.create(ids.next(), projectId, DocSource.Kind.DOCUMENTS, location,
                ExclusionPatterns.of(exclusions));
        DocumentFolder.Scan scan = folder.scan(candidate);
        if (scan.problem() != null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Ce dossier ne peut pas être lu : " + location);
        }
        return new Preview(scan.retained().size(), scan.excluded().size(), scan.ignored().size(),
                scan.retained().stream().map(DocumentFolder.FileEntry::path).limit(EXAMPLES).toList(),
                scan.excluded().stream().limit(EXAMPLES).toList(), scan.ignored().stream().limit(EXAMPLES).toList(),
                scan.limitReached());
    }

    /** Supprime la source ; un dossier de documents emporte ses éléments de l'index (006). */
    public void delete(UUID projectId, UUID sourceId) {
        DocSource source = owned(projectId, sourceId);
        docSources.delete(source.id());
        if (source.kind() == DocSource.Kind.DOCUMENTS) {
            index.retainUnder(projectId, DocumentFolder.prefix(source), Set.of());
        }
    }

    private DocSource owned(UUID projectId, UUID sourceId) {
        requireProject(projectId);
        return docSources.find(sourceId).filter(s -> s.projectId().equals(projectId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
