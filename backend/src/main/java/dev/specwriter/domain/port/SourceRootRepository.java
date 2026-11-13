package dev.specwriter.domain.port;

import dev.specwriter.domain.sources.SourceRoot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Racines de sources des projets (013). */
public interface SourceRootRepository {

    SourceRoot save(SourceRoot root);

    Optional<SourceRoot> find(UUID projectId, UUID rootId);

    /** Racines du projet, dans l'ordre de leur déclaration. */
    List<SourceRoot> list(UUID projectId);

    void delete(UUID projectId, UUID rootId);
}
