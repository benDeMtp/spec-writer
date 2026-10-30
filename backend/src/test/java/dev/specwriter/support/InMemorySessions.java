package dev.specwriter.support;

import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionListing;
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
        return all.stream().map(s -> new SessionListing(s.id(), s.name(), s.state(), s.updatedAt(), true)).toList();
    }

    @Override
    public void delete(UUID id) {
        sessions.remove(id);
        questionnaires.remove(id);
    }
}
