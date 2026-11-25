package fr.kawamind.domain.port;

import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.InterviewSpec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Séances, leurs spécifications et leurs points (021, V30). */
public interface InterviewRepository {

    /**
     * Enregistre la séance et ses spécifications, dans l'ordre de la liste ; une seule séance en
     * cours par spécification ({@code INTERVIEW_BUSY} sinon, FR-007).
     */
    void save(Interview interview, List<InterviewSpec> specs);

    /** Enregistre la séance seule (curseur, état), ses spécifications inchangées. */
    void save(Interview interview);

    Optional<Interview> find(UUID id);

    List<InterviewSpec> specs(UUID interviewId);

    /** Une spécification de la séance (version appliquée, FR-014). */
    void saveSpec(InterviewSpec spec);

    /** Séances du projet dans cet état, les plus récentes d'abord. */
    List<Interview> byState(UUID projectId, Interview.State state);

    /** Séances qui comptent cette spécification, les plus récentes d'abord. */
    List<Interview> forSession(UUID sessionId);

    /** Séance en cours sur cette spécification. */
    Optional<UUID> inProgress(UUID sessionId);

    void delete(UUID id);

    void savePoint(InterviewPoint point);

    Optional<InterviewPoint> point(UUID id);

    /** Points de la séance, dans l'ordre de création. */
    List<InterviewPoint> points(UUID interviewId);

    int countPoints(UUID interviewId, UUID sessionId);

    void deletePoint(UUID id);

    /** Rattache le message de discussion qui porte les décisions de la séance (research.md R4). */
    void tagMessage(UUID messageId, UUID interviewId);

    /** Séance dont ce message de discussion porte les décisions ; vide pour un message ordinaire. */
    Optional<UUID> interviewOfMessage(UUID messageId);
}
