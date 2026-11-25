package fr.kawamind.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.Specs;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Issue de séance appliquée à la révision (021, research.md R3). */
class InterviewOutcomeTest {

    private static final SourceDeviation.Cited REF = new SourceDeviation.Cited("jira:AV-1", null, null, null);
    private static final SourceDeviation.Cited INTERP = new SourceDeviation.Cited("doc:avoirs", null, null, null);
    private static final LocalDate DUE = LocalDate.of(2026, 11, 27);

    private static Assumption h(String code, String statement, String from) {
        return new Assumption(code, statement, Assumption.Origin.TOOL_INFERENCE, null, null, from);
    }

    private static SpecificationContent base() {
        return Specs.valid()
                .withAssumptions(List.of(h("H-01", "Plafond TTC", null), h("H-02", "Validation manager", null),
                        h("H-03", "Remboursement par virement", null)))
                .withSourceDeviations(List.of(new SourceDeviation(REF, INTERP, "500 contre 1 000 euros")))
                .withOpenQuestions(List.of(new OpenQuestion("Q-01", "Quel délai ?", null, null, null, null)));
    }

    @Test
    void hypotheseEtPointOuvertTranchesRetires() {
        SpecificationContent revised = Specs.valid()
                .withAssumptions(List.of(h("H-01", "Plafond TTC", "H-01"), h("H-02", "Validation manager reformulée", "H-02")))
                .withOpenQuestions(List.of(new OpenQuestion("Q-01", "Quel délai ?", null, null, null, "Q-01")));
        SpecificationContent out = InterviewOutcome.apply(revised, base(),
                new InterviewOutcome.Decisions(Set.of("H-01", "Q-01"), List.of()));
        assertThat(out.assumptions()).extracting(Assumption::code, Assumption::statement, Assumption::fromCode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("H-01", "Validation manager reformulée", null));
        assertThat(out.openQuestions()).isEmpty();
    }

    @Test
    void ecartTrancheRetire() {
        SpecificationContent revised = Specs.valid().withSourceDeviations(
                List.of(new SourceDeviation(REF, INTERP, "500 contre 1 000 euros").withFromCode("EC-01")));
        SpecificationContent out = InterviewOutcome.apply(revised, base(),
                new InterviewOutcome.Decisions(Set.of("EC-01"), List.of()));
        assertThat(out.sourceDeviations()).isEmpty();
    }

    @Test
    void pointReporteAjouteCommePointOuvert() {
        Anchor step = new Anchor("UC-01", 2, false);
        Anchor gone = new Anchor("UC-04", 1, false);
        SpecificationContent revised = Specs.valid()
                .withAssumptions(List.of(h("H-01", "Remboursement par virement", "H-03")));
        SpecificationContent out = InterviewOutcome.apply(revised, base(), new InterviewOutcome.Decisions(Set.of("H-03"),
                List.of(new InterviewOutcome.Deferred("Qui valide en l'absence du manager ?", "Paul D.", DUE, step, null),
                        new InterviewOutcome.Deferred("Virement ou avoir ?", null, null, gone, "H-03"))));
        assertThat(out.assumptions()).isEmpty();
        assertThat(out.openQuestions()).containsExactly(
                new OpenQuestion("Q-01", "Qui valide en l'absence du manager ?", "Paul D.", DUE, step, null),
                new OpenQuestion("Q-02", "Virement ou avoir ?", null, null, null, null));
    }

    @Test
    void pasDeDoublonQuandLaQuestionEstRepriseParFromCode() {
        SpecificationContent revised = Specs.valid().withOpenQuestions(List.of(
                new OpenQuestion("Q-01", "Nouvelle question", null, null, null, null),
                new OpenQuestion("Q-02", "Quel délai ?", null, null, null, "Q-01")));
        SpecificationContent out = InterviewOutcome.apply(revised, base(), new InterviewOutcome.Decisions(Set.of("Q-01"),
                List.of(new InterviewOutcome.Deferred("Quel délai ? À voir avec la compta", "Julie M.", DUE, null, "Q-01"))));
        assertThat(out.openQuestions()).extracting(OpenQuestion::code, OpenQuestion::question, OpenQuestion::owner)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Q-01", "Nouvelle question", null),
                        org.assertj.core.groups.Tuple.tuple("Q-02", "Quel délai ?", "Julie M."));
    }

    @Test
    void hypotheseSansFromCodeOuFromCodeInconnuGardee() {
        SpecificationContent revised = Specs.valid()
                .withAssumptions(List.of(h("H-01", "Nouvelle", null), h("H-02", "Inventée", "H-09")));
        SpecificationContent out = InterviewOutcome.apply(revised, base(),
                new InterviewOutcome.Decisions(Set.of("H-09", "H-01"), List.of()));
        assertThat(out.assumptions()).extracting(Assumption::code).containsExactly("H-01", "H-02");
    }

    @Test
    void hypotheseDUneQuestionPasseeTrancheeRetireeMemeRajouteeParLeCode() {
        UUID question = UUID.randomUUID();
        Assumption skipped = new Assumption("H-01", "« Durée » : 1 h (valeur par défaut, question passée)",
                Assumption.Origin.SKIPPED_QUESTION, question);
        SpecificationContent out = InterviewOutcome.apply(Specs.valid().withAssumptions(List.of(skipped)),
                Specs.valid().withAssumptions(List.of(skipped)), new InterviewOutcome.Decisions(Set.of("H-01"), List.of()));
        assertThat(out.assumptions()).isEmpty();
    }

    @Test
    void sansChangementRedigeLaBaseRepriseSeVoitAppliquerLIssue() {
        SpecificationContent out = InterviewOutcome.apply(InterviewOutcome.unchanged(base()), base(),
                new InterviewOutcome.Decisions(Set.of("H-02", "EC-01"), List.of()));
        assertThat(out.assumptions()).extracting(Assumption::statement)
                .containsExactly("Plafond TTC", "Remboursement par virement");
        assertThat(out.sourceDeviations()).isEmpty();
        assertThat(out.openQuestions()).extracting(OpenQuestion::code).containsExactly("Q-01");
    }

    @Test
    void decisionsDesPointsDeLaSeance() {
        UUID i = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        List<InterviewPoint> points = List.of(
                new InterviewPoint(UUID.randomUUID(), i, s, null, InterviewPoint.Origin.ASSUMPTION, "H-01", "",
                        InterviewPoint.Outcome.CONFIRMED, null, null, null, now, now),
                new InterviewPoint(UUID.randomUUID(), i, s, null, InterviewPoint.Origin.ASSUMPTION, "H-02", "",
                        null, null, null, null, now, now),
                new InterviewPoint(UUID.randomUUID(), i, s, new Anchor("UC-01", 1, false), InterviewPoint.Origin.QUESTION,
                        null, "Qui valide ?", InterviewPoint.Outcome.DEFERRED, "Paul D.", DUE, null, now, now),
                new InterviewPoint(UUID.randomUUID(), i, s, null, InterviewPoint.Origin.REMARK, null, "Bien vu", null,
                        null, null, null, now, now));
        InterviewOutcome.Decisions d = InterviewOutcome.Decisions.of(points);
        assertThat(d.decided()).containsExactly("H-01");
        assertThat(d.deferred()).containsExactly(
                new InterviewOutcome.Deferred("Qui valide ?", "Paul D.", DUE, new Anchor("UC-01", 1, false), null));
    }
}
