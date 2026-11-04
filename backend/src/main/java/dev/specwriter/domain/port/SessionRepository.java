package dev.specwriter.domain.port;

import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Implementation;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionListing;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Sessions et questionnaires, sauvegardés ensemble après chaque réponse (FR-016). */
public interface SessionRepository {

    void save(Session session, Questionnaire questionnaire);

    /** Lève SESSION_UNREADABLE si la sauvegarde est endommagée. */
    Optional<Session> find(UUID id);

    Optional<Questionnaire> questionnaire(UUID sessionId);

    /** Les plus récemment modifiées d'abord ; une session illisible y figure, marquée comme telle. */
    List<SessionListing> list();

    void delete(UUID id);

    /** Statut d'implémentation, lu à chaque garde du verrou ; vide si la session n'existe pas. */
    Optional<Implementation> implementation(UUID sessionId);

    /** Seule écriture du statut : {@link #save} ne le touche pas (research.md R7). */
    void saveImplementation(UUID sessionId, Implementation implementation);
}
