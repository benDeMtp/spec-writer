package dev.specwriter.domain.port;

import dev.specwriter.domain.project.DocSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Sources de documentation des projets (data-model.md de 002, ProjectDocSource). */
public interface DocSourceRepository {

    void save(DocSource source);

    Optional<DocSource> find(UUID id);

    /** Dans l'ordre d'ajout. */
    List<DocSource> list(UUID projectId);

    void delete(UUID id);
}
