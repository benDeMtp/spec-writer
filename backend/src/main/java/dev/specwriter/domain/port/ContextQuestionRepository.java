package dev.specwriter.domain.port;

import dev.specwriter.domain.project.ContextQuestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Questions de clarification répondues ou passées d'un projet (FR-013i). */
public interface ContextQuestionRepository {

    void save(ContextQuestion question);

    /** Par numéro croissant. */
    List<ContextQuestion> list(UUID projectId);

    Optional<ContextQuestion> find(UUID projectId, int number);

    /** Numéro suivant dans le projet, à partir de 1. */
    int nextNumber(UUID projectId);
}
