package fr.kawamind.application.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.Walkthrough;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.specification.VersionOrigin;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryInterviews;
import fr.kawamind.support.InMemorySessions;
import fr.kawamind.support.InMemorySpecifications;
import fr.kawamind.support.SequentialIdGenerator;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 021, US1 : « Faire le point », parcours, points notés, une séance en cours par spécification. */
class RunInterviewTest {

    static final UUID PROJECT = new UUID(9, 9);

    final FixedClock clock = new FixedClock();
    final InMemorySessions sessions = new InMemorySessions();
    final InMemorySpecifications versions = new InMemorySpecifications();
    final InMemoryInterviews interviews = new InMemoryInterviews();
    final OperationRegistry operations = new OperationRegistry();
    final RunInterview run = new RunInterview(interviews, sessions, versions, operations, clock, new SequentialIdGenerator());

    Session specification(String name) {
        Session s = Session.start(UUID.randomUUID(), PROJECT,
                Need.of("Le gestionnaire doit pouvoir émettre un avoir partiel sur une facture."), name, clock.now());
        sessions.save(s, Questionnaire.empty());
        SpecificationContent content = Specs.valid().withAssumptions(List.of(new Assumption("H-01", "Le montant est TTC.",
                Assumption.Origin.TOOL_INFERENCE, null, new Anchor("UC-01", 2, false), null)));
        versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 1, content, "fp", VersionOrigin.GENERATED, "# v1",
                clock.now()));
        return s;
    }

    RunInterview.PointInput answer(UUID session, String text, InterviewPoint.Outcome outcome) {
        return new RunInterview.PointInput(session, new Anchor("UC-01", 2, false), InterviewPoint.Origin.ASSUMPTION, "H-01",
                text, outcome, null, null, null);
    }

    @Test
    void faireLePointDemarreUneSeanceSurLaDerniereVersion() {
        Session s = specification("Avoirs");
        versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 2, Specs.valid(), "fp2", VersionOrigin.DISCUSSION,
                "# v2", clock.now()));
        RunInterview.View v = run.startForSpecification(s.id(), List.of("Julie M."));
        assertThat(v.interview().state()).isEqualTo(Interview.State.IN_PROGRESS);
        assertThat(v.interview().participants()).containsExactly("Julie M.");
        assertThat(v.specs()).singleElement().satisfies(spec -> {
            assertThat(spec.spec().version()).isEqualTo(2);
            assertThat(spec.latestVersion()).isEqualTo(2);
            assertThat(spec.session().name()).isEqualTo("Avoirs");
        });
        assertThat(v.walkthrough().stops()).isNotEmpty();
        assertThat(interviews.inProgress(s.id())).contains(v.interview().id());
    }

    @Test
    void lesParticipantsDeLaSeancePrecedenteSontRepris() {
        Session s = specification("Avoirs");
        RunInterview.View first = run.startForSpecification(s.id(), List.of("Julie M.", "Paul D."));
        run.savePoint(first.interview().id(), null, answer(s.id(), "", InterviewPoint.Outcome.CONFIRMED));
        run.finish(first.interview().id());
        assertThat(run.startForSpecification(s.id(), List.of()).interview().participants())
                .containsExactly("Julie M.", "Paul D.");
    }

    @Test
    void refus() {
        Session s = specification("Avoirs");
        run.startForSpecification(s.id(), List.of());
        assertThatThrownBy(() -> run.startForSpecification(s.id(), List.of()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERVIEW_BUSY));

        Session other = specification("Relances");
        try (OperationRegistry.Operation op = operations.begin(other.id())) {
            assertThatThrownBy(() -> run.startForSpecification(other.id(), List.of()))
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.OPERATION_IN_PROGRESS));
        }
        assertThatThrownBy(() -> run.startForSpecification(UUID.randomUUID(), List.of()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void pointsNotesEtMisAJour() {
        Session s = specification("Avoirs");
        UUID id = run.startForSpecification(s.id(), List.of()).interview().id();

        InterviewPoint a = run.savePoint(id, null, answer(s.id(), "", InterviewPoint.Outcome.CONFIRMED));
        clock.advance(Duration.ofSeconds(5));
        // la même hypothèse répondue une seconde fois : le point est mis à jour, pas dupliqué
        InterviewPoint again = run.savePoint(id, null, answer(s.id(), "Plafonné au TTC.", InterviewPoint.Outcome.CHANGE));
        assertThat(again.id()).isEqualTo(a.id());
        assertThat(again.createdAt()).isEqualTo(a.createdAt());
        assertThat(again.updatedAt()).isEqualTo(clock.now());

        InterviewPoint remark = run.savePoint(id, null, new RunInterview.PointInput(s.id(), new Anchor("UC-01", 1, false),
                InterviewPoint.Origin.REMARK, null, "Validation du manager", InterviewPoint.Outcome.CHANGE, null, null, null));
        InterviewPoint edited = run.savePoint(id, remark.id(), new RunInterview.PointInput(s.id(),
                new Anchor("UC-01", 1, false), InterviewPoint.Origin.REMARK, null, "Validation du manager au-delà de 500 €",
                InterviewPoint.Outcome.CHANGE, null, null, null));
        assertThat(edited.text()).endsWith("500 €");

        Walkthrough.Counts counts = run.view(id).walkthrough().counts();
        assertThat(counts).isEqualTo(new Walkthrough.Counts(1, 1, 0));

        run.deletePoint(id, remark.id());
        assertThat(interviews.point(remark.id())).isEmpty();
        // un point de la spécification ne se supprime pas : on change sa réponse
        assertThatThrownBy(() -> run.deletePoint(id, a.id())).isInstanceOf(DomainException.class);
    }

    @Test
    void ancreEtCodeValidesContreLaVersionRelue() {
        Session s = specification("Avoirs");
        UUID id = run.startForSpecification(s.id(), List.of()).interview().id();
        assertThatThrownBy(() -> run.savePoint(id, null, new RunInterview.PointInput(s.id(), new Anchor("UC-07", null, false),
                InterviewPoint.Origin.REMARK, null, "x", InterviewPoint.Outcome.CHANGE, null, null, null)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> run.savePoint(id, null, new RunInterview.PointInput(s.id(), null,
                InterviewPoint.Origin.ASSUMPTION, "H-09", "", InterviewPoint.Outcome.CONFIRMED, null, null, null)))
                .isInstanceOf(DomainException.class);
        // une spécification hors de la séance
        Session other = specification("Relances");
        assertThatThrownBy(() -> run.savePoint(id, null, answer(other.id(), "", InterviewPoint.Outcome.CONFIRMED)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void curseur() {
        Session s = specification("Avoirs");
        UUID id = run.startForSpecification(s.id(), List.of()).interview().id();
        run.moveCursor(id, s.id(), new Anchor("UC-01", 2, false));
        assertThat(run.view(id).interview().cursor()).isEqualTo(new Interview.Cursor(s.id(), new Anchor("UC-01", 2, false)));
    }

    @Test
    void uneRelectureTermineeSansRienNoterNEstPasEnregistree() {
        Session s = specification("Avoirs");
        UUID id = run.startForSpecification(s.id(), List.of("Julie M.")).interview().id();
        assertThat(run.finish(id)).isEmpty();
        assertThat(interviews.find(id)).isEmpty();
        assertThat(interviews.inProgress(s.id())).isEmpty();
        assertThat(run.forSpecification(s.id())).isEmpty();
    }

    @Test
    void uneSeanceTermineeRefuseTouteModification() {
        Session s = specification("Avoirs");
        UUID id = run.startForSpecification(s.id(), List.of()).interview().id();
        run.savePoint(id, null, answer(s.id(), "", InterviewPoint.Outcome.CONFIRMED));
        RunInterview.View finished = run.finish(id).orElseThrow();
        assertThat(finished.interview().state()).isEqualTo(Interview.State.FINISHED);
        assertThat(interviews.inProgress(s.id())).isEmpty();
        assertThatThrownBy(() -> run.savePoint(id, null, answer(s.id(), "", InterviewPoint.Outcome.CONFIRMED)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERVIEW_NOT_IN_PROGRESS));
        assertThat(run.forSpecification(s.id())).extracting(Interview::id).containsExactly(id);
    }

    @Test
    void compteRendu() {
        Session s = specification("Avoirs");
        UUID id = run.startForSpecification(s.id(), List.of("Julie M.")).interview().id();
        run.savePoint(id, null, answer(s.id(), "", InterviewPoint.Outcome.CONFIRMED));
        String text = run.report(id).text();
        assertThat(text).startsWith("Point sur « Avoirs » — ").contains(", avec Julie M.")
                .contains("• H-01 (UC-01, étape 2) — confirmé : Le montant est TTC.");
        assertThat(run.report(id).html()).startsWith("<h2>Point sur « Avoirs »");
    }
}
