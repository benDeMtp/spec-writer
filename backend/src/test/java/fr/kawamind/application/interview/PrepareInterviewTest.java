package fr.kawamind.application.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.PendingPoints;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 021, US5 : préparer une séance de plusieurs spécifications. */
class PrepareInterviewTest {

    static final UUID PROJECT = new UUID(9, 9);

    final FixedClock clock = new FixedClock();
    final InMemorySessions sessions = new InMemorySessions();
    final InMemorySpecifications versions = new InMemorySpecifications();
    final InMemoryInterviews interviews = new InMemoryInterviews();
    final OperationRegistry operations = new OperationRegistry();
    final SequentialIdGenerator ids = new SequentialIdGenerator();
    final RunInterview run = new RunInterview(interviews, sessions, versions, operations, clock, ids);
    final PrepareInterview prepare = new PrepareInterview(interviews, sessions, versions, operations, run, clock, ids);

    Session specification(String name, int versionCount) {
        Session s = Session.start(UUID.randomUUID(), PROJECT, Need.of("Le gestionnaire doit pouvoir émettre un avoir partiel sur une facture."), name,
                clock.now());
        sessions.save(s, Questionnaire.empty());
        SpecificationContent content = Specs.valid().withAssumptions(List.of(
                new Assumption("H-01", "Montant TTC", Assumption.Origin.TOOL_INFERENCE, null),
                new Assumption("H-02", "Avoir partiel", Assumption.Origin.TOOL_INFERENCE, null)));
        for (int n = 1; n <= versionCount; n++) {
            versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), n, content, "fp", VersionOrigin.GENERATED,
                    "# v" + n, clock.now()));
        }
        return s;
    }

    static PrepareInterview.Choice choice(Session s, String... excluded) {
        return new PrepareInterview.Choice(s.id(), Set.of(excluded));
    }

    static void refused(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void creerModifierSupprimerUneSeancePreparee() {
        Session a = specification("Avoirs", 1);
        Session b = specification("Relances", 1);
        RunInterview.View v = prepare.prepare(PROJECT, List.of(choice(a), choice(b, "H-02")), List.of("Julie M."));
        UUID id = v.interview().id();
        assertThat(v.interview().state()).isEqualTo(Interview.State.PREPARED);
        assertThat(v.specs()).extracting(s -> s.session().name()).containsExactly("Avoirs", "Relances");
        assertThat(interviews.specs(id).get(1).excluded()).containsExactly("H-02");
        assertThat(interviews.specs(id)).allSatisfy(s -> assertThat(s.version()).isNull());

        // ordre changé, une spécification retirée
        prepare.update(id, List.of(choice(b)), List.of("Julie M.", "Paul D."));
        assertThat(run.view(id).specs()).extracting(s -> s.session().name()).containsExactly("Relances");
        assertThat(run.view(id).interview().participants()).containsExactly("Julie M.", "Paul D.");
        assertThat(prepare.preparedFor(PROJECT)).extracting(Interview::id).containsExactly(id);

        prepare.delete(id);
        assertThat(interviews.find(id)).isEmpty();
    }

    @Test
    void lesPointsEcartesSontDesCodesDeLaDerniereVersion() {
        Session a = specification("Avoirs", 1);
        refused(() -> prepare.prepare(PROJECT, List.of(choice(a, "H-09")), List.of()), ErrorCode.VALIDATION_FAILED);
        refused(() -> prepare.prepare(PROJECT, List.of(), List.of()), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void demarrerFixeLaVersionEtRefuseUneSpecificationOccupee() {
        Session a = specification("Avoirs", 2);
        Session b = specification("Relances", 1);
        UUID id = prepare.prepare(PROJECT, List.of(choice(a), choice(b)), List.of()).interview().id();
        UUID busy = run.startForSpecification(b.id(), List.of()).interview().id();
        assertThatThrownBy(() -> prepare.start(id, List.of())).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.INTERVIEW_BUSY);
            assertThat(e.userMessage()).contains("« Relances »");
        });
        run.finish(busy);

        RunInterview.View started = prepare.start(id, List.of("Julie M."));
        assertThat(started.interview().state()).isEqualTo(Interview.State.IN_PROGRESS);
        assertThat(started.interview().participants()).containsExactly("Julie M.");
        assertThat(interviews.specs(id)).extracting(s -> s.version()).containsExactly(2, 1);
        // une séance qui a démarré ne se modifie ni ne se supprime plus
        refused(() -> prepare.update(id, List.of(choice(a)), List.of()), ErrorCode.INTERVIEW_NOT_PREPARED);
        refused(() -> prepare.delete(id), ErrorCode.INTERVIEW_NOT_PREPARED);
    }

    @Test
    void uneSeancePrepareeSansSpecificationDisparait() {
        Session a = specification("Avoirs", 1);
        UUID id = prepare.prepare(PROJECT, List.of(choice(a)), List.of()).interview().id();
        // la spécification supprimée sort de la séance (cascade de V30)
        interviews.save(interviews.find(id).orElseThrow(), List.of());
        assertThat(prepare.preparedFor(PROJECT)).isEmpty();
        assertThat(interviews.find(id)).isEmpty();
    }

    @Test
    void pointsATraiterMoinsCeuxTranchesNonAppliques() {
        Session a = specification("Avoirs", 1);
        assertThat(prepare.pending(a.id())).contains(new PendingPoints(2, 0, 0));
        UUID id = run.startForSpecification(a.id(), List.of()).interview().id();
        run.savePoint(id, null, new RunInterview.PointInput(a.id(), null, InterviewPoint.Origin.ASSUMPTION, "H-01", "",
                InterviewPoint.Outcome.CONFIRMED, null, null, null));
        // en cours : rien n'est encore tranché
        assertThat(prepare.pending(a.id())).contains(new PendingPoints(2, 0, 0));
        run.finish(id);
        assertThat(prepare.pending(a.id())).contains(new PendingPoints(1, 0, 0));
        interviews.saveSpec(interviews.specs(id).getFirst().withApplied(2));
        assertThat(prepare.pending(a.id())).contains(new PendingPoints(2, 0, 0));
        assertThat(prepare.pending(specification("Vide", 0).id())).isEmpty();
    }
}
