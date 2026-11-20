package fr.kawamind.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.scenario.AcceptanceScenario;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.Requirement;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.UseCase;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 021, FR-003 : ordre de parcours, points sur leur élément, « Non rattachés », points écartés. */
class WalkthroughTest {

    static final UUID S1 = new UUID(0, 1);
    static final UUID S2 = new UUID(0, 2);
    static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");

    static SpecificationContent content() {
        UseCase uc = new UseCase("UC-01", "Émettre un avoir", "gestionnaire", "Créditer", List.of(),
                List.of("Ouvrir la facture", "Choisir les lignes", "Saisir le montant"), List.of("Facture soldée"),
                new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "avoir"));
        AcceptanceScenario sc = new AcceptanceScenario("SC-01-01", "UC-01", "Avoir partiel", AcceptanceScenario.Kind.NOMINAL,
                "gestionnaire", List.of(), List.of("je saisis 50 €"), List.of("l'avoir est émis"), false);
        List<Assumption> assumptions = List.of(
                new Assumption("H-01", "Le montant est TTC.", Assumption.Origin.TOOL_INFERENCE, null,
                        new Anchor("UC-01", 2, false), null),
                new Assumption("H-02", "Un seul avoir par facture.", Assumption.Origin.TOOL_INFERENCE, null,
                        new Anchor("UC-09", null, false), null),
                new Assumption("H-03", "Pas de validation.", Assumption.Origin.TOOL_INFERENCE, null));
        return new SpecificationContent("Avoirs", "Émission des avoirs.", "", List.of(uc), List.of(sc), assumptions,
                List.of(new Requirement("ET-01", Requirement.Category.TECHNICAL, "Journaliser"),
                        new Requirement("EF-01", Requirement.Category.FUNCTIONAL, "Tracer l'avoir")))
                .withOpenQuestions(List.of(new OpenQuestion("Q-01", "Quel délai ?", null, null,
                        new Anchor("SC-01-01", null, false), null)));
    }

    static InterviewPoint point(UUID session, Anchor anchor, InterviewPoint.Origin origin, String code,
            InterviewPoint.Outcome outcome, String text) {
        return new InterviewPoint(UUID.randomUUID(), new UUID(0, 99), session, anchor, origin, code, text, outcome, null,
                null, null, T0, T0);
    }

    @Test
    void ordreEtRattachement() {
        InterviewPoint remark = point(S1, new Anchor("UC-01", 3, false), InterviewPoint.Origin.REMARK, null,
                InterviewPoint.Outcome.CHANGE, "Validation du manager");
        Walkthrough w = Walkthrough.of(List.of(new Walkthrough.SpecInput(S1, content(), Set.of(), List.of(remark))),
                Language.FR);

        assertThat(w.stops()).extracting(Walkthrough.Stop::label).containsExactly(
                "Résumé", "UC-01", "UC-01, étape 1", "UC-01, étape 2", "UC-01, étape 3", "UC-01, alternative 1",
                "SC-01-01", "EF-01", "ET-01", "Non rattachés");
        assertThat(w.stops().get(4).text()).isEqualTo("Saisir le montant");
        assertThat(w.stops().get(3).items()).extracting(Walkthrough.Item::code).containsExactly("H-01");
        assertThat(w.stops().get(4).items()).extracting(i -> i.point().text()).containsExactly("Validation du manager");
        assertThat(w.stops().get(6).items()).extracting(Walkthrough.Item::code).containsExactly("Q-01");
        // ancre invalide ou absente : « Non rattachés »
        assertThat(w.stops().getLast().kind()).isEqualTo(Walkthrough.Kind.UNANCHORED);
        assertThat(w.stops().getLast().items()).extracting(Walkthrough.Item::code).containsExactly("H-02", "H-03");
    }

    @Test
    void reponseRattacheeAuPointDeLaSpecification() {
        InterviewPoint answer = point(S1, new Anchor("UC-01", 2, false), InterviewPoint.Origin.ASSUMPTION, "H-01",
                InterviewPoint.Outcome.CONFIRMED, "");
        Walkthrough w = Walkthrough.of(List.of(new Walkthrough.SpecInput(S1, content(), Set.of(), List.of(answer))),
                Language.FR);
        Walkthrough.Item h1 = w.stops().get(3).items().getFirst();
        assertThat(h1.point()).isEqualTo(answer);
        assertThat(h1.pending()).isFalse();
        assertThat(w.counts()).isEqualTo(new Walkthrough.Counts(1, 0, 3));
    }

    @Test
    void pointsEcartesEtProchainPoint() {
        Walkthrough w = Walkthrough.of(List.of(new Walkthrough.SpecInput(S1, content(), Set.of("H-02", "H-03"), List.of())),
                Language.FR);
        assertThat(w.stops()).extracting(Walkthrough.Stop::kind).doesNotContain(Walkthrough.Kind.UNANCHORED);
        assertThat(w.nextPending(0)).hasValue(3);
        assertThat(w.nextPending(3)).hasValue(6);
        assertThat(w.nextPending(6)).isEmpty();
        assertThat(w.counts()).isEqualTo(new Walkthrough.Counts(0, 0, 2));
    }

    @Test
    void plusieursSpecificationsDansLeurRang() {
        Walkthrough w = Walkthrough.of(List.of(new Walkthrough.SpecInput(S2, content(), Set.of(), List.of()),
                new Walkthrough.SpecInput(S1, content(), Set.of(), List.of())), Language.FR);
        assertThat(w.stops().getFirst().sessionId()).isEqualTo(S2);
        assertThat(w.stops().getLast().sessionId()).isEqualTo(S1);
        assertThat(w.stops()).hasSize(20);
    }

    @Test
    void remarquesEtQuestionsComptees() {
        InterviewPoint remark = point(S1, new Anchor(Anchor.SUMMARY, null, false), InterviewPoint.Origin.REMARK, null,
                InterviewPoint.Outcome.CHANGE, "Préciser");
        InterviewPoint question = point(S1, null, InterviewPoint.Origin.QUESTION, null, null, "Qui ?");
        Walkthrough w = Walkthrough.of(List.of(new Walkthrough.SpecInput(S1, content(), Set.of("H-02", "H-03"),
                List.of(remark, question))), Language.FR);
        // une question sans ancre va dans « Non rattachés », même quand rien d'autre n'y est
        assertThat(w.stops().getLast().items()).extracting(i -> i.point().text()).containsExactly("Qui ?");
        assertThat(w.counts()).isEqualTo(new Walkthrough.Counts(0, 2, 2));
    }
}
