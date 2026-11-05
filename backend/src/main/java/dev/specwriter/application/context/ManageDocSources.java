package dev.specwriter.application.context;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.DocSource;
import java.util.List;
import java.util.UUID;

/** Sources de documentation Antora d'un projet, 10 au plus (FR-013). */
public class ManageDocSources {

    private final ProjectRepository projects;
    private final DocSourceRepository docSources;
    private final IdGenerator ids;

    public ManageDocSources(ProjectRepository projects, DocSourceRepository docSources, IdGenerator ids) {
        this.projects = projects;
        this.docSources = docSources;
        this.ids = ids;
    }

    public List<DocSource> list(UUID projectId) {
        requireProject(projectId);
        return docSources.list(projectId);
    }

    public DocSource add(UUID projectId, DocSource.Kind kind, String location) {
        requireProject(projectId);
        DocSource.requireRoomFor(docSources.list(projectId).size());
        DocSource source = DocSource.create(ids.next(), projectId, kind, location);
        docSources.save(source);
        return source;
    }

    public void delete(UUID projectId, UUID sourceId) {
        requireProject(projectId);
        DocSource source = docSources.find(sourceId).filter(s -> s.projectId().equals(projectId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        docSources.delete(source.id());
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
