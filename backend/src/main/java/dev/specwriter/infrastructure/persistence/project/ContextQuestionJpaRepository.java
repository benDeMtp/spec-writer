package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.domain.port.ContextQuestionRepository;
import dev.specwriter.domain.project.ContextQuestion;
import dev.specwriter.domain.project.ContextSheet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ContextQuestionJpaRepository implements ContextQuestionRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(ContextQuestion q) {
        ContextQuestionEntity e = new ContextQuestionEntity();
        e.id = q.id().toString();
        e.projectId = q.projectId().toString();
        e.number = q.number();
        e.sheet = q.sheet().name();
        e.label = q.label();
        e.rationale = q.rationale();
        e.answer = q.answer();
        e.state = q.state().name();
        e.askedAt = q.askedAt();
        e.answeredAt = q.answeredAt();
        em.merge(e);
    }

    @Override
    @Transactional
    public List<ContextQuestion> list(UUID projectId) {
        return em.createQuery("select q from ContextQuestionEntity q where q.projectId = :p order by q.number",
                ContextQuestionEntity.class).setParameter("p", projectId.toString()).getResultStream()
                .map(ContextQuestionJpaRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public Optional<ContextQuestion> find(UUID projectId, int number) {
        return em.createQuery("select q from ContextQuestionEntity q where q.projectId = :p and q.number = :n",
                ContextQuestionEntity.class).setParameter("p", projectId.toString()).setParameter("n", number)
                .getResultStream().findFirst().map(ContextQuestionJpaRepository::toDomain);
    }

    @Override
    @Transactional
    public int nextNumber(UUID projectId) {
        Integer max = em.createQuery("select max(q.number) from ContextQuestionEntity q where q.projectId = :p",
                Integer.class).setParameter("p", projectId.toString()).getSingleResult();
        return max == null ? 1 : max + 1;
    }

    private static ContextQuestion toDomain(ContextQuestionEntity e) {
        return new ContextQuestion(UUID.fromString(e.id), UUID.fromString(e.projectId), e.number,
                ContextSheet.valueOf(e.sheet), e.label, e.rationale, e.answer, ContextQuestion.State.valueOf(e.state),
                e.askedAt, e.answeredAt);
    }
}
