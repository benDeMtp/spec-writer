package dev.specwriter.application.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.session.AnswerQuestion;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.Contradiction;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.DraftSpecification.DraftScenario;
import dev.specwriter.domain.llm.DraftSpecification.DraftSource;
import dev.specwriter.domain.llm.DraftSpecification.DraftUseCase;
import dev.specwriter.domain.llm.ProposeQuestionsResult;
import dev.specwriter.domain.llm.ProposedQuestion;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.SessionState;
import dev.specwriter.domain.specification.Assumption;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySessions;
import dev.specwriter.support.InMemorySpecifications;
import dev.specwriter.support.SequentialIdGenerator;
import dev.specwriter.support.StubLlm;
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
}
