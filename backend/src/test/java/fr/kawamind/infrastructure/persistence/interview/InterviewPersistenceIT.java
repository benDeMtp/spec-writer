package fr.kawamind.infrastructure.persistence.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.InterviewSpec;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.Anchor;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 021 : séances, spécifications et points (V30). */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class InterviewPersistenceIT {

    static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");

    @Inject
    InterviewRepository interviews;

    @Inject
    ProjectRepository projects;

    @Inject
    SessionRepository sessions;

    final List<UUID> createdProjects = new ArrayList<>();
    final List<UUID> createdSessions = new ArrayList<>();
    final List<UUID> createdInterviews = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdInterviews.forEach(id -> interviews.find(id).ifPresent(i -> interviews.delete(id)));
        createdSessions.forEach(id -> sessions.find(id).ifPresent(s -> sessions.delete(id)));
        createdProjects.forEach(id -> projects.find(id).ifPresent(p -> projects.delete(id)));
    }

    Project project() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Séances " + UUID.randomUUID()), null, null, T0);
        projects.save(p);
        createdProjects.add(p.id());
        return p;
    }

    Session session(Project p) {
        Session s = Session.start(UUID.randomUUID(), p.id(),
                Need.of("Le gestionnaire doit pouvoir émettre un avoir partiel sur une facture."), null, T0);
        sessions.save(s, Questionnaire.empty());
        createdSessions.add(s.id());
        return s;
    }

    Interview interview(Project p, Interview.State state) {
        Interview i = state == Interview.State.IN_PROGRESS
                ? Interview.startNow(UUID.randomUUID(), p.id(), List.of("Julie M.", "Paul D."), T0)
                : Interview.prepare(UUID.randomUUID(), p.id(), List.of("Julie M."), T0);
        createdInterviews.add(i.id());
        return i;
    }

    @Test
    void allerRetourSeanceSpecificationsPoints() {
        Project p = project();
        Session s1 = session(p);
        Session s2 = session(p);
        Interview i = interview(p, Interview.State.PREPARED);
        interviews.save(i, List.of(new InterviewSpec(i.id(), s2.id(), 1, null, Set.of("H-02"), null),
                new InterviewSpec(i.id(), s1.id(), 2, null, Set.of(), null)));

        assertThat(interviews.find(i.id())).contains(i);
        assertThat(interviews.specs(i.id())).extracting(InterviewSpec::sessionId).containsExactly(s2.id(), s1.id());
        assertThat(interviews.specs(i.id()).getFirst().excluded()).containsExactly("H-02");
        assertThat(interviews.byState(p.id(), Interview.State.PREPARED)).extracting(Interview::id).containsExactly(i.id());

        Interview started = i.start(T0.plusSeconds(60))
                .withCursor(new Interview.Cursor(s1.id(), new Anchor("UC-01", 2, false)));
        interviews.save(started, List.of(new InterviewSpec(i.id(), s2.id(), 1, 3, Set.of("H-02"), null),
                new InterviewSpec(i.id(), s1.id(), 2, 1, Set.of(), null)));
        assertThat(interviews.find(i.id())).contains(started);
        assertThat(interviews.inProgress(s1.id())).contains(i.id());
        assertThat(interviews.forSession(s1.id())).extracting(Interview::id).containsExactly(i.id());

        InterviewPoint answer = new InterviewPoint(UUID.randomUUID(), i.id(), s1.id(), new Anchor("UC-01", 2, false),
                InterviewPoint.Origin.ASSUMPTION, "H-01", "Plafonné au TTC.", InterviewPoint.Outcome.CHANGE, null, null,
                null, T0, T0);
        InterviewPoint question = new InterviewPoint(UUID.randomUUID(), i.id(), s1.id(), null,
                InterviewPoint.Origin.QUESTION, null, "Qui valide ?", null, "Paul D.", LocalDate.of(2026, 11, 27),
                new InterviewPoint.DesignatedSource("DOC_PAGE", "https://wiki/4512"), T0.plusSeconds(1), T0.plusSeconds(1));
        interviews.savePoint(answer);
        interviews.savePoint(question);
        assertThat(interviews.points(i.id())).containsExactly(answer, question);
        assertThat(interviews.countPoints(i.id(), s1.id())).isEqualTo(2);
        assertThat(interviews.countPoints(i.id(), s2.id())).isZero();

        interviews.deletePoint(question.id());
        assertThat(interviews.point(question.id())).isEmpty();

        Interview finished = started.finish(T0.plusSeconds(120));
        interviews.save(finished);
        assertThat(interviews.inProgress(s1.id())).isEmpty();
        interviews.saveSpec(interviews.specs(i.id()).get(1).withApplied(2));
        assertThat(interviews.specs(i.id()).get(1).appliedVersion()).isEqualTo(2);
    }

    @Test
    void uneSeuleSeanceEnCoursParSpecification() {
        Project p = project();
        Session s = session(p);
        Interview first = interview(p, Interview.State.IN_PROGRESS);
        interviews.save(first, List.of(new InterviewSpec(first.id(), s.id(), 1, 1, Set.of(), null)));
        Interview second = interview(p, Interview.State.IN_PROGRESS);
        assertThatThrownBy(() -> interviews.save(second, List.of(new InterviewSpec(second.id(), s.id(), 1, 1, Set.of(),
                null)))).isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERVIEW_BUSY));
        createdInterviews.remove(second.id());
        assertThat(interviews.find(second.id())).isEmpty();
    }

    @Test
    void suppressionDeLaSpecificationEnCascade() {
        Project p = project();
        Session s = session(p);
        Interview i = interview(p, Interview.State.IN_PROGRESS);
        interviews.save(i, List.of(new InterviewSpec(i.id(), s.id(), 1, 1, Set.of(), null)));
        interviews.savePoint(new InterviewPoint(UUID.randomUUID(), i.id(), s.id(), null, InterviewPoint.Origin.REMARK,
                null, "À revoir", InterviewPoint.Outcome.CHANGE, null, null, null, T0, T0));
        sessions.delete(s.id());
        assertThat(interviews.specs(i.id())).isEmpty();
        assertThat(interviews.points(i.id())).isEmpty();
    }
}
