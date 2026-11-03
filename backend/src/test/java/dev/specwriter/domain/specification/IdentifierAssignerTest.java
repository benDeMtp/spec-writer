package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.DraftSpecification.DraftAssumption;
import dev.specwriter.domain.llm.DraftSpecification.DraftRequirement;
import dev.specwriter.domain.llm.DraftSpecification.DraftScenario;
import dev.specwriter.domain.llm.DraftSpecification.DraftSource;
import dev.specwriter.domain.llm.DraftSpecification.DraftUseCase;
import dev.specwriter.domain.llm.ProposedQuestion;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Language;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class IdentifierAssignerTest {

    static DraftUseCase uc(String title, DraftSource source) {
        return new DraftUseCase(title, "client", "payer", List.of(), List.of("valider"), List.of(), source);
    }

    static DraftScenario sc(int useCase, String kind) {
        return new DraftScenario(useCase, "Titre", kind, "client", List.of(), List.of("je paie"), List.of("c'est payé"),
                false);
    }

    static List<Question> treated() {
        AtomicLong n = new AtomicLong();
        Questionnaire qn = Questionnaire.empty().addBatch(List.of(
                new ProposedQuestion(QuestionTheme.GOAL, "Objectif ?", "Pour cadrer", List.of(), "Gagner du temps", List.of()),
                new ProposedQuestion(QuestionTheme.ERRORS, "Erreurs ?", "Pour les cas d'erreur", List.of(), null, List.of())),
                List.of(), Set.of(), () -> new UUID(0, n.incrementAndGet()));
        Instant at = Instant.EPOCH;
        return qn.answer(new UUID(0, 1), "Réduire les erreurs", at).skip(new UUID(0, 2), at).treated();
    }

    @Test
    void codesAttribuesDansLOrdreQuelleQueSoitLaSortie() {
        DraftSource need = new DraftSource("NEED_EXCERPT", "", "payer");
        DraftSpecification draft = new DraftSpecification(" Paiement ", "Résumé",
                List.of(uc("Payer", need), uc("Rembourser", need)),
                List.of(sc(1, "NOMINAL"), sc(0, "nominal"), sc(1, "ERROR"), sc(0, "EDGE")),
                List.of(new DraftAssumption("Le paiement est en euros", "TOOL_INFERENCE", "")),
                List.of(new DraftRequirement("TECHNICAL", "Chiffrement TLS"), new DraftRequirement("FUNCTIONAL", "Reçu"),
                        new DraftRequirement("FUNCTIONAL", "Historique")));
        IdentifierAssigner.Assigned a = IdentifierAssigner.assign(draft, List.of(), Language.FR);

        assertThat(a.violations().isEmpty()).isTrue();
        SpecificationContent c = a.content();
        assertThat(c.title()).isEqualTo("Paiement");
        assertThat(c.useCases()).extracting(UseCase::code).containsExactly("UC-01", "UC-02");
        assertThat(c.scenarios()).extracting(AcceptanceScenario::code)
                .containsExactly("SC-02-01", "SC-01-01", "SC-02-02", "SC-01-02");
        assertThat(c.scenarios()).extracting(AcceptanceScenario::useCaseCode).containsExactly("UC-02", "UC-01", "UC-02", "UC-01");
        assertThat(c.assumptions()).extracting(Assumption::code).containsExactly("H-01");
        assertThat(c.requirements()).extracting(Requirement::code).containsExactly("ET-01", "EF-01", "EF-02");
    }

    @Test
    void questionPasseeAjouteeAuxHypotheses() {
        List<Question> treated = treated();
        DraftSpecification draft = new DraftSpecification("T", "R",
                List.of(uc("Payer", new DraftSource("ANSWER", new UUID(0, 1).toString(), "Réduire les erreurs"))),
                List.of(sc(0, "NOMINAL")), List.of(), List.of());
        SpecificationContent c = IdentifierAssigner.assign(draft, treated, Language.FR).content();
        assertThat(c.useCases().getFirst().source().questionId()).isEqualTo(new UUID(0, 1));
        assertThat(c.assumptions()).singleElement().satisfies(h -> {
            assertThat(h.origin()).isEqualTo(Assumption.Origin.SKIPPED_QUESTION);
            assertThat(h.questionId()).isEqualTo(new UUID(0, 2));
            assertThat(h.statement()).contains("Erreurs ?").contains(Question.UNSPECIFIED).contains("question passée");
        });
        // déjà citée par le LLM : pas de doublon
        DraftSpecification cited = new DraftSpecification("T", "R", draft.useCases(), draft.scenarios(),
                List.of(new DraftAssumption("Pas de gestion d'erreur précisée", "SKIPPED_QUESTION", new UUID(0, 2).toString())),
                List.of());
        assertThat(IdentifierAssigner.assign(cited, treated, Language.EN).content().assumptions()).hasSize(1);
    }

    @Test
    void identifiantInventeParLeLlmIgnore() {
        DraftSpecification draft = new DraftSpecification("T", "R",
                List.of(uc("Payer", new DraftSource("ANSWER", "q-inventee", "x"))), List.of(sc(0, "NOMINAL")),
                List.of(new DraftAssumption("Hypothèse", "SKIPPED_QUESTION", "q-inventee")), List.of());
        SpecificationContent c = IdentifierAssigner.assign(draft, List.of(), Language.FR).content();
        assertThat(c.useCases().getFirst().source().questionId()).isNull();
        assertThat(c.assumptions().getFirst().origin()).isEqualTo(Assumption.Origin.TOOL_INFERENCE);
    }

    @Test
    void sortieIncompleteSignalee() {
        DraftSpecification draft = new DraftSpecification("", "R",
                List.of(new DraftUseCase("", "client", "payer", List.of(), List.of(" "), List.of(), null)),
                List.of(sc(0, "BIZARRE")), List.of(), List.of(new DraftRequirement("AUTRE", "x")));
        StructureViolations v = IdentifierAssigner.assign(draft, List.of(), Language.FR).violations();
        assertThat(v.messages()).anyMatch(m -> m.contains("titre et le résumé"))
                .anyMatch(m -> m.contains("UC-01 : titre"))
                .anyMatch(m -> m.contains("déroulement nominal"))
                .anyMatch(m -> m.contains("source est obligatoire"))
                .anyMatch(m -> m.contains("type de scénario"))
                .anyMatch(m -> m.contains("exigence"));
        DraftSpecification badSource = new DraftSpecification("T", "R", List.of(uc("Payer", new DraftSource("X", "", ""))),
                List.of(), List.of(), List.of());
        assertThat(IdentifierAssigner.assign(badSource, List.of(), Language.FR).violations().messages())
                .anyMatch(m -> m.contains("type de source"));
    }

    @Test
    void resumeEtDetailsBornes() {
        DraftSource need = new DraftSource("NEED_EXCERPT", "", "payer");
        DraftSpecification ok = new DraftSpecification("T", "r".repeat(4_000), "d".repeat(20_000),
                List.of(uc("Payer", need)), List.of(sc(0, "NOMINAL")), List.of(), List.of());
        DraftSpecification tooLong = new DraftSpecification("T", "r".repeat(4_001), "d".repeat(20_001),
                ok.useCases(), ok.scenarios(), List.of(), List.of());

        IdentifierAssigner.Assigned a = IdentifierAssigner.assign(ok, List.of(), Language.FR);
        assertThat(a.violations().isEmpty()).isTrue();
        assertThat(a.content().details()).hasSize(20_000);
        assertThat(IdentifierAssigner.assign(tooLong, List.of(), Language.FR).violations().messages())
                .contains("le résumé dépasse 4 000 caractères", "les détails dépassent 20 000 caractères");
    }

    @Test
    void detailsAbsentsDeLaSortieVides() {
        DraftSpecification draft = new DraftSpecification("T", "R", List.of(), List.of(), List.of(), List.of());

        assertThat(draft.details()).isEmpty();
        assertThat(IdentifierAssigner.assign(draft, List.of(), Language.FR).content().details()).isEmpty();
    }

    @Test
    void titreTronqueACentVingtCaracteres() {
        DraftSpecification draft = new DraftSpecification("t".repeat(200), "R", List.of(), List.of(), List.of(), List.of());
        assertThat(IdentifierAssigner.assign(draft, List.of(), Language.FR).content().title()).hasSize(120);
    }
}
