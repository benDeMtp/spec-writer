package dev.specwriter.infrastructure.persistence.session;

import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.question.Answer;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.QuestionState;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionOrigin;
import dev.specwriter.domain.session.SessionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class SessionJpaRepository implements SessionRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(Session s, Questionnaire q) {
        SessionEntity e = Optional.ofNullable(em.find(SessionEntity.class, s.id().toString())).orElseGet(SessionEntity::new);
        e.id = s.id().toString();
        e.name = s.name();
        e.need = s.need().text();
        e.language = s.language().name();
        e.origin = s.origin().name();
        e.state = s.state().name();
        e.coveredThemes = q.coveredByNeed().stream().map(Enum::name).sorted().toList();
        e.createdAt = s.createdAt();
        e.updatedAt = s.updatedAt();
        em.merge(e);
        for (Question question : q.questions()) {
            em.merge(toEntity(s.id(), question));
            AnswerEntity existing = em.find(AnswerEntity.class, question.id().toString());
            if (question.answer() == null) {
                if (existing != null) {
                    em.remove(existing);
                }
            } else {
                AnswerEntity a = existing == null ? new AnswerEntity() : existing;
                a.questionId = question.id().toString();
                a.value = question.answer().value();
                a.assumption = question.answer().assumption();
                a.answeredAt = question.answer().answeredAt();
                em.merge(a);
            }
        }
    }

    @Override
    @Transactional
    public Optional<Session> find(UUID id) {
        return Optional.ofNullable(em.find(SessionEntity.class, id.toString())).map(SessionJpaRepository::toDomain);
    }

    @Override
    @Transactional
    public Optional<Questionnaire> questionnaire(UUID sessionId) {
        SessionEntity session = em.find(SessionEntity.class, sessionId.toString());
        if (session == null) {
            return Optional.empty();
        }
        List<QuestionEntity> questions = em.createQuery(
                        "select q from QuestionEntity q where q.sessionId = :s order by q.ordinal", QuestionEntity.class)
                .setParameter("s", sessionId.toString()).getResultList();
        Map<String, AnswerEntity> answers = questions.isEmpty() ? Map.of() : em.createQuery(
                        "select a from AnswerEntity a where a.questionId in :ids", AnswerEntity.class)
                .setParameter("ids", questions.stream().map(q -> q.id).toList()).getResultList().stream()
                .collect(Collectors.toMap(a -> a.questionId, Function.identity()));
        List<Question> domain = questions.stream().map(q -> toDomain(q, answers.get(q.id))).toList();
        return Optional.of(new Questionnaire(domain,
                session.coveredThemes.stream().map(QuestionTheme::valueOf).collect(Collectors.toSet())));
    }

    @Override
    @Transactional
    public List<Session> list() {
        return em.createQuery("select s from SessionEntity s order by s.updatedAt desc", SessionEntity.class)
                .getResultList().stream().map(SessionJpaRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        // Suppression en cascade par la base (V3, V4) : réponses, questions et versions
        em.createNativeQuery("delete from session where id = ?1").setParameter(1, id.toString()).executeUpdate();
        em.clear();
    }

    private static QuestionEntity toEntity(UUID sessionId, Question q) {
        QuestionEntity e = new QuestionEntity();
        e.id = q.id().toString();
        e.sessionId = sessionId.toString();
        e.ordinal = q.order();
        e.theme = q.theme().name();
        e.batch = q.batch();
        e.label = q.label();
        e.rationale = q.rationale();
        e.suggestions = q.suggestions();
        e.defaultValue = q.defaultValue();
        e.dependsOn = q.dependsOn().stream().map(UUID::toString).toList();
        e.state = q.state().name();
        return e;
    }

    private static Question toDomain(QuestionEntity e, AnswerEntity a) {
        UUID id = UUID.fromString(e.id);
        Answer answer = a == null ? null : new Answer(id, a.value, a.assumption, a.answeredAt);
        return new Question(id, e.ordinal, QuestionTheme.valueOf(e.theme), e.batch, e.label, e.rationale, e.suggestions,
                e.defaultValue, e.dependsOn.stream().map(UUID::fromString).toList(), QuestionState.valueOf(e.state),
                answer);
    }

    private static Session toDomain(SessionEntity e) {
        return new Session(UUID.fromString(e.id), e.name, new Need(e.need), Language.valueOf(e.language),
                SessionOrigin.valueOf(e.origin), SessionState.valueOf(e.state), e.createdAt, e.updatedAt);
    }
}
