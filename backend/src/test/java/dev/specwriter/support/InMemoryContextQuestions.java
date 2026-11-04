package dev.specwriter.support;

import dev.specwriter.domain.port.ContextQuestionRepository;
import dev.specwriter.domain.project.ContextQuestion;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Questions de clarification en mémoire. */
public class InMemoryContextQuestions implements ContextQuestionRepository {
    private final Map<UUID, ContextQuestion> questions = new LinkedHashMap<>();

    @Override
    public void save(ContextQuestion question) {
        questions.put(question.id(), question);
    }

    @Override
    public List<ContextQuestion> list(UUID projectId) {
        return questions.values().stream().filter(q -> q.projectId().equals(projectId))
                .sorted(Comparator.comparingInt(ContextQuestion::number)).toList();
    }

    @Override
    public Optional<ContextQuestion> find(UUID projectId, int number) {
        return list(projectId).stream().filter(q -> q.number() == number).findFirst();
    }

    @Override
    public int nextNumber(UUID projectId) {
        return list(projectId).stream().mapToInt(ContextQuestion::number).max().orElse(0) + 1;
    }
}
