package fr.kawamind.support;

import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Implementation;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.session.SessionListing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemorySessions implements SessionRepository {
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final Map<UUID, Questionnaire> questionnaires = new LinkedHashMap<>();
    private final Map<UUID, Implementation> implementations = new LinkedHashMap<>();
    public int saves;

    @Override
    public void save(Session session, Questionnaire questionnaire) {
        sessions.put(session.id(), session);
        questionnaires.put(session.id(), questionnaire);
        saves++;
    }

    @Override
    public Optional<Session> find(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public Optional<Questionnaire> questionnaire(UUID sessionId) {
        return Optional.ofNullable(questionnaires.get(sessionId));
    }

    @Override
    public List<SessionListing> list() {
        List<Session> all = new ArrayList<>(sessions.values());
        all.sort(Comparator.comparing(Session::updatedAt).reversed());
        return all.stream().map(s -> new SessionListing(s.id(), s.projectId(), s.name(), s.state(), s.updatedAt(), true,
                implementation(s.id()).orElseThrow().status())).toList();
    }

    @Override
    public void delete(UUID id) {
        sessions.remove(id);
        questionnaires.remove(id);
        implementations.remove(id);
    }

    @Override
    public Optional<Implementation> implementation(UUID sessionId) {
        return sessions.containsKey(sessionId)
                ? Optional.of(implementations.getOrDefault(sessionId, Implementation.initial()))
                : Optional.empty();
    }

    @Override
    public void saveImplementation(UUID sessionId, Implementation implementation) {
        if (!sessions.containsKey(sessionId)) {
            throw new fr.kawamind.domain.error.DomainException(fr.kawamind.domain.error.ErrorCode.NOT_FOUND);
        }
        implementations.put(sessionId, implementation);
    }
}
