package fr.kawamind.domain.port;

import fr.kawamind.domain.interview.SourceUpdate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** « À reporter » des spécifications (021, US6, V30). */
public interface SourceUpdateRepository {

    void save(SourceUpdate update);

    Optional<SourceUpdate> find(UUID id);

    /** « À reporter » ouverts de la spécification, dans l'ordre de création. */
    List<SourceUpdate> open(UUID sessionId);
}
