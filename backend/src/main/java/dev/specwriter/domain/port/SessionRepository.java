package dev.specwriter.domain.port;

import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Sessions et questionnaires, sauvegardés ensemble après chaque réponse (FR-016). */
public interface SessionRepository {

    void save(Session session, Questionnaire questionnaire);

    Optional<Session> find(UUID id);

    Optional<Questionnaire> questionnaire(UUID sessionId);

    /** Les plus récemment modifiées d'abord. */
    List<Session> list();

    void delete(UUID id);
}
