package fr.kawamind.support;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.InterviewSpec;
import fr.kawamind.domain.port.InterviewRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Séances en mémoire, avec la règle d'une séance en cours par spécification (V30). */
public class InMemoryInterviews implements InterviewRepository {

    private final Map<UUID, Interview> interviews = new LinkedHashMap<>();
    private final Map<UUID, List<InterviewSpec>> specs = new LinkedHashMap<>();
    private final Map<UUID, InterviewPoint> points = new LinkedHashMap<>();
    private final Map<UUID, UUID> messages = new LinkedHashMap<>();

    @Override
    public void save(Interview interview, List<InterviewSpec> list) {
        if (interview.state() == Interview.State.IN_PROGRESS) {
            for (InterviewSpec s : list) {
                inProgress(s.sessionId()).filter(other -> !other.equals(interview.id())).ifPresent(other -> {
                    throw new DomainException(ErrorCode.INTERVIEW_BUSY);
                });
            }
        }
        interviews.put(interview.id(), interview);
        specs.put(interview.id(), List.copyOf(list));
    }

    @Override
    public void save(Interview interview) {
        interviews.put(interview.id(), interview);
    }

    @Override
    public Optional<Interview> find(UUID id) {
        return Optional.ofNullable(interviews.get(id));
    }

    @Override
    public List<InterviewSpec> specs(UUID interviewId) {
        return specs.getOrDefault(interviewId, List.of()).stream().sorted(Comparator.comparingInt(InterviewSpec::rank))
                .toList();
    }

    @Override
    public void saveSpec(InterviewSpec spec) {
        List<InterviewSpec> list = new ArrayList<>(specs.getOrDefault(spec.interviewId(), List.of()));
        list.replaceAll(s -> s.sessionId().equals(spec.sessionId()) ? spec : s);
        specs.put(spec.interviewId(), List.copyOf(list));
    }

    @Override
    public List<Interview> byState(UUID projectId, Interview.State state) {
        return interviews.values().stream().filter(i -> i.projectId().equals(projectId) && i.state() == state)
                .sorted(Comparator.comparing(Interview::createdAt).reversed()).toList();
    }

    @Override
    public List<Interview> forSession(UUID sessionId) {
        return interviews.values().stream()
                .filter(i -> specs.getOrDefault(i.id(), List.of()).stream().anyMatch(s -> s.sessionId().equals(sessionId)))
                .sorted(Comparator.comparing(Interview::createdAt).reversed()).toList();
    }

    @Override
    public Optional<UUID> inProgress(UUID sessionId) {
        return interviews.values().stream().filter(i -> i.state() == Interview.State.IN_PROGRESS)
                .filter(i -> specs.getOrDefault(i.id(), List.of()).stream().anyMatch(s -> s.sessionId().equals(sessionId)))
                .map(Interview::id).findFirst();
    }

    @Override
    public void delete(UUID id) {
        interviews.remove(id);
        specs.remove(id);
        points.values().removeIf(p -> p.interviewId().equals(id));
    }

    @Override
    public void savePoint(InterviewPoint point) {
        points.put(point.id(), point);
    }

    @Override
    public Optional<InterviewPoint> point(UUID id) {
        return Optional.ofNullable(points.get(id));
    }

    @Override
    public List<InterviewPoint> points(UUID interviewId) {
        return points.values().stream().filter(p -> p.interviewId().equals(interviewId))
                .sorted(Comparator.comparing(InterviewPoint::createdAt)).toList();
    }

    @Override
    public int countPoints(UUID interviewId, UUID sessionId) {
        return (int) points.values().stream()
                .filter(p -> p.interviewId().equals(interviewId) && p.sessionId().equals(sessionId)).count();
    }

    @Override
    public void deletePoint(UUID id) {
        points.remove(id);
    }

    @Override
    public void tagMessage(UUID messageId, UUID interviewId) {
        messages.put(messageId, interviewId);
    }

    @Override
    public Optional<UUID> interviewOfMessage(UUID messageId) {
        return Optional.ofNullable(messages.get(messageId));
    }
}
