package fr.kawamind.application.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.session.AnswerQuestion;
import fr.kawamind.application.session.SessionSnapshot;
import fr.kawamind.application.session.StartSession;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.llm.Contradiction;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.DraftSpecification.DraftScenario;
import fr.kawamind.domain.llm.DraftSpecification.DraftSource;
import fr.kawamind.domain.llm.DraftSpecification.DraftUseCase;
import fr.kawamind.domain.llm.ProposeQuestionsResult;
import fr.kawamind.domain.llm.ProposedQuestion;
import fr.kawamind.domain.question.QuestionTheme;
import fr.kawamind.domain.session.SessionState;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryConsents;
import fr.kawamind.support.InMemoryProjects;
import fr.kawamind.support.InMemorySessions;
import fr.kawamind.support.InMemorySpecifications;
import fr.kawamind.support.SequentialIdGenerator;
import fr.kawamind.support.StubLlm;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenerateSpecificationTest {

    static final String NEED = "Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée.";

    final InMemorySessions sessions = new InMemorySessions();
    final InMemorySpecifications versions = new InMemorySpecifications();
    final InMemoryConsents consents = new InMemoryConsents();
    final StubLlm llm = new StubLlm();
    final FixedClock clock = new FixedClock();
    final SequentialIdGenerator ids = new SequentialIdGenerator();
    final ConsentPolicy policy = new ConsentPolicy(consents);
    GenerateSpecification generate;
    UUID sessionId;

    static DraftSpecification valid(String title) {
        return new DraftSpecification(title, "Réserver une salle",
                List.of(new DraftUseCase("Réserver", "employé", "réserver une salle", List.of(), List.of("choisir"), List.of(),
                        new DraftSource("NEED_EXCERPT", "", "réserver une salle"))),
                List.of(new DraftScenario(0, "Réservation", "NOMINAL", "employé", List.of(), List.of("je réserve"),
                        List.of("la salle est réservée"), false)),
                List.of(), List.of());
    }

    static DraftSpecification invalid() {
        return new DraftSpecification("T", "R",
                List.of(new DraftUseCase("Réserver", "employé", "réserver", List.of(), List.of("choisir"), List.of(),
                        new DraftSource("NEED_EXCERPT", "", "réserver"))),
                List.of(new DraftScenario(0, "Sans action", "NOMINAL", "employé", List.of(), List.of(), List.of("ok"), false)),
                List.of(), List.of());
    }

    @BeforeEach
    void setUp() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        llm.questions.add(new ProposeQuestionsResult(List.of(new ProposedQuestion(QuestionTheme.GOAL, "Objectif ?",
                "Pour cadrer", List.of(), "Gagner du temps", List.of())), List.of(), Set.of()));
        SessionSnapshot s = new StartSession(sessions, new InMemoryProjects(), llm, policy, clock, ids).run(NEED, null);
        sessionId = s.session().id();
        llm.questions.add(StubLlm.none());
        new AnswerQuestion(sessions, new InMemoryProjects(), llm, policy, clock, ids).run(sessionId, s.questionnaire().currentBatch().getFirst().id(),
                "", true);
        generate = new GenerateSpecification(sessions, new InMemoryProjects(), versions, llm, policy, clock, ids, "0.1.0");
    }

    @Test
    void premiereGenerationVersionUnAvecHypotheseDeLaQuestionPassee() {
        llm.drafts.add(valid("Réservation"));
        GenerateSpecification.Generated g = generate.run(sessionId);
        assertThat(g.reused()).isFalse();
        assertThat(g.version().number()).isEqualTo(1);
        assertThat(g.version().content().useCases().getFirst().code()).isEqualTo("UC-01");
        assertThat(g.version().content().assumptions()).singleElement()
                .satisfies(a -> assertThat(a.origin()).isEqualTo(Assumption.Origin.SKIPPED_QUESTION));
        assertThat(g.version().markdown()).contains("#### SC-01-01 : Réservation").contains("## Hypothèses");
        assertThat(sessions.find(sessionId).orElseThrow().state()).isEqualTo(SessionState.GENERATED);
        assertThat(llm.generationRequests.getFirst().answers()).singleElement()
                .satisfies(a -> assertThat(a.assumption()).isTrue());
    }

    /** Le nom tiré du besoin laisse place au titre de la première version ; un nom choisi est gardé. */
    @Test
    void lePremierTitreDevientLeNomSaufSiLaSpecificationAEteRenommee() {
        llm.drafts.add(valid("Réservation de salles"));
        generate.run(sessionId);
        assertThat(sessions.find(sessionId).orElseThrow().name()).isEqualTo("Réservation de salles");

        var session = sessions.find(sessionId).orElseThrow();
        sessions.save(session.rename("Mon nom", clock.now()), sessions.questionnaire(sessionId).orElseThrow());
        llm.drafts.add(valid("Autre titre"));
        versions.deleteAll(sessionId);
        generate.run(sessionId);
        assertThat(sessions.find(sessionId).orElseThrow().name()).isEqualTo("Mon nom");
    }

    @Test
    void entreesIdentiquesVersionReutiliseeSansAppel() {
        llm.drafts.add(valid("Réservation"));
        GenerateSpecification.Generated first = generate.run(sessionId);
        GenerateSpecification.Generated second = generate.run(sessionId);
        assertThat(second.reused()).isTrue();
        assertThat(second.version()).isEqualTo(first.version());
        assertThat(llm.generationRequests).hasSize(1);
    }

    @Test
    void sortieInvalideRedemandeeAvecSesEcartsPuisAcceptee() {
        llm.drafts.add(invalid());
        llm.drafts.add(valid("Réservation"));
        GenerateSpecification.Generated g = generate.run(sessionId);
        assertThat(g.version().number()).isEqualTo(1);
        assertThat(llm.generationRequests).hasSize(2);
        assertThat(llm.generationRequests.get(0).correction()).isEmpty();
        assertThat(llm.generationRequests.get(1).correction()).contains("SC-01-01").contains("Quand");
    }

    @Test
    void troisSortiesInvalidesAucuneVersionEnregistree() {
        llm.drafts.add(invalid());
        llm.drafts.add(invalid());
        llm.drafts.add(invalid());
        assertThatThrownBy(() -> generate.run(sessionId)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT));
        assertThat(versions.list(sessionId)).isEmpty();
        assertThat(llm.generationRequests).hasSize(3);
        assertThat(sessions.find(sessionId).orElseThrow().state()).isEqualTo(SessionState.READY_TO_GENERATE);
    }

    @Test
    void numerosDeVersionSuccessifsQuandLesEntreesChangent() {
        llm.drafts.add(valid("V1"));
        generate.run(sessionId);
        GenerateSpecification otherTool = new GenerateSpecification(sessions, new InMemoryProjects(), versions, llm, policy, clock, ids, "0.2.0");
        llm.drafts.add(valid("V2"));
        assertThat(otherTool.run(sessionId).version().number()).isEqualTo(2);
        assertThat(versions.list(sessionId)).hasSize(2);
    }

    @Test
    void consentementEtContradictionVerifies() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, false, Instant.EPOCH.plusSeconds(1)));
        assertThatThrownBy(() -> generate.run(sessionId)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThat(llm.generationRequests).isEmpty();

        var s = sessions.find(sessionId).orElseThrow();
        var withContradiction = sessions.questionnaire(sessionId).orElseThrow().addBatch(List.of(),
                List.of(new Contradiction("x", "Payant ?")), Set.of(), UUID::randomUUID);
        sessions.save(s, withContradiction);
        assertThatThrownBy(() -> generate.run(sessionId)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTRADICTION_UNRESOLVED));
        assertThatThrownBy(() -> generate.run(UUID.randomUUID())).isInstanceOf(DomainException.class);
    }

    @Test
    void llmIndisponible() {
        assertThatThrownBy(() -> generate.run(sessionId)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_UNAVAILABLE));
        assertThat(sessions.questionnaire(sessionId).orElseThrow().treated()).hasSize(1);
    }

    @Test
    void lePerimetreRepriseDonneSaRubriqueEtSesHypotheses() {
        fr.kawamind.support.InMemoryPerimeters perimeters = new fr.kawamind.support.InMemoryPerimeters();
        perimeters.saveSnapshot(sessionId, new fr.kawamind.domain.sources.PerimeterSnapshot(List.of(
                new fr.kawamind.domain.sources.PerimeterSnapshot.Entry("facturation", "modules/facturation", "rv",
                        fr.kawamind.domain.sources.Qualification.IMPACTED, "Facture les contrats actifs.",
                        List.of(new fr.kawamind.domain.sources.Reason.Term("ContractStatus", 2)), List.of()),
                new fr.kawamind.domain.sources.PerimeterSnapshot.Entry("relance", "modules/relance", "rv",
                        fr.kawamind.domain.sources.Qualification.TO_CHECK, null, List.of(), List.of())), false),
                Instant.EPOCH);
        GenerateSpecification withPerimeter = new GenerateSpecification(sessions, versions, llm, policy, clock, ids, "0.1.0",
                fr.kawamind.application.knowledge.SessionKnowledge.sheetOnly(new InMemoryProjects()),
                new OperationRegistry(), perimeters);
        llm.drafts.add(valid("Réservation"));

        GenerateSpecification.Generated g = withPerimeter.run(sessionId);

        // l'hypothèse de la question passée d'abord, puis celle de l'outil pour le module à vérifier
        assertThat(g.version().content().assumptions()).extracting(Assumption::code, Assumption::origin).containsExactly(
                org.assertj.core.groups.Tuple.tuple("H-01", Assumption.Origin.SKIPPED_QUESTION),
                org.assertj.core.groups.Tuple.tuple("H-02", Assumption.Origin.TOOL_INFERENCE));
        assertThat(g.version().content().assumptions().get(1).statement())
                .isEqualTo("Le module relance (modules/relance) est à vérifier.");
        assertThat(g.version().markdown()).contains("## Modules touchés").contains("- **facturation** (rv/modules/facturation)")
                .contains("**H-02** : Le module relance (modules/relance) est à vérifier.");
        // la sortie reste conforme au contrôle de traçabilité
        java.util.Set<UUID> treated = new java.util.HashSet<>();
        sessions.questionnaire(sessionId).orElseThrow().treated().forEach(q -> treated.add(q.id()));
        assertThat(fr.kawamind.domain.specification.TraceabilityValidator.validate(g.version().content(), treated)
                .isEmpty()).isTrue();
    }
}
