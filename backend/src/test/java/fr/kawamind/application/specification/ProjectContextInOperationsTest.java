package fr.kawamind.application.specification;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.session.AnswerQuestion;
import fr.kawamind.application.session.SessionSnapshot;
import fr.kawamind.application.session.StartSession;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.DraftSpecification.DraftScenario;
import fr.kawamind.domain.llm.DraftSpecification.DraftSource;
import fr.kawamind.domain.llm.DraftSpecification.DraftUseCase;
import fr.kawamind.domain.llm.ProposeQuestionsResult;
import fr.kawamind.domain.llm.ProposedQuestion;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectContext;
import fr.kawamind.domain.question.QuestionTheme;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SpecificationVersion;
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

/** La fiche générale est transmise au LLM, citée et retenue sur chaque version (FR-011, FR-013d, T021, T109). */
class ProjectContextInOperationsTest {

    static final String NEED = "Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée.";
    static final String CONTEXT = "## Acteurs\n- Le **gestionnaire** valide les réservations.";
    static final Instant CONTEXT_AT = Instant.parse("2026-11-02T10:00:00Z");

    final InMemorySessions sessions = new InMemorySessions();
    final InMemoryProjects projects = new InMemoryProjects();
    final InMemorySpecifications versions = new InMemorySpecifications();
    final InMemoryConsents consents = new InMemoryConsents();
    final StubLlm llm = new StubLlm();
    final FixedClock clock = new FixedClock();
    final SequentialIdGenerator ids = new SequentialIdGenerator();
    final ConsentPolicy policy = new ConsentPolicy(consents);
    GenerateSpecification generate;
    UUID sessionId;

    static DraftSpecification citingContext(String excerpt) {
        return new DraftSpecification("Réservation", "Réserver une salle",
                List.of(new DraftUseCase("Valider", "gestionnaire", "valider une réservation", List.of(), List.of("valider"),
                        List.of(), new DraftSource("PROJECT_CONTEXT", "", excerpt))),
                List.of(new DraftScenario(0, "Validation", "NOMINAL", "gestionnaire", List.of(), List.of("je valide"),
                        List.of("la réservation est validée"), false)),
                List.of(), List.of());
    }

    static final String TECHNICAL = "## Architecture\n- Les réservations passent par l'API REST du service salles.";

    void setContext(String markdown, Instant at) {
        Project p = projects.find(Project.DEFAULT_ID).orElseThrow();
        projects.save(p.withContext(ProjectContext.edited(markdown, at)));
    }

    @BeforeEach
    void setUp() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        setContext(CONTEXT, CONTEXT_AT);
        llm.questions.add(new ProposeQuestionsResult(List.of(new ProposedQuestion(QuestionTheme.GOAL, "Objectif ?",
                "Pour cadrer", List.of(), "Gagner du temps", List.of())), List.of(), Set.of()));
        SessionSnapshot s = new StartSession(sessions, projects, llm, policy, clock, ids).run(NEED, null);
        sessionId = s.session().id();
        llm.questions.add(StubLlm.none());
        new AnswerQuestion(sessions, projects, llm, policy, clock, ids).run(sessionId,
                s.questionnaire().currentBatch().getFirst().id(), "", true);
        generate = new GenerateSpecification(sessions, projects, versions, llm, policy, clock, ids, "0.1.0");
    }

    @Test
    void questionsEtGenerationRecoiventLeContexte() {
        assertThat(llm.questionRequests).hasSize(2)
                .allSatisfy(r -> assertThat(r.knowledge()).isEqualTo(ProjectKnowledge.sheet(CONTEXT, CONTEXT_AT)));
        llm.drafts.add(citingContext("Le gestionnaire valide les réservations."));
        SpecificationVersion v = generate.run(sessionId).version();
        assertThat(llm.generationRequests.getFirst().knowledge()).isEqualTo(ProjectKnowledge.sheet(CONTEXT, CONTEXT_AT));
        assertThat(v.content().useCases().getFirst().source().kind()).isEqualTo(SourceRef.Kind.PROJECT_CONTEXT);
        assertThat(v.contextHash()).isEqualTo(ProjectKnowledge.sheet(CONTEXT, CONTEXT_AT).sheetHash());
        assertThat(v.knowledge().generalSheetHash()).isEqualTo(v.contextHash());
        assertThat(v.contextUpdatedAt()).isEqualTo(CONTEXT_AT);
    }

    @Test
    void extraitAbsentDuContexteRedemande() {
        llm.drafts.add(citingContext("Le directeur signe."));
        llm.drafts.add(citingContext("Le gestionnaire valide les réservations."));
        generate.run(sessionId);
        assertThat(llm.generationRequests).hasSize(2);
        assertThat(llm.generationRequests.get(1).correction()).contains("fiche générale");
    }

    @Test
    void unContexteModifieDonneUneNouvelleVersion() {
        llm.drafts.add(citingContext("Le gestionnaire valide les réservations."));
        SpecificationVersion first = generate.run(sessionId).version();
        assertThat(generate.run(sessionId).reused()).isTrue();

        setContext(CONTEXT + "\n- Le comptable facture.", CONTEXT_AT.plusSeconds(60));
        llm.drafts.add(citingContext("Le gestionnaire valide les réservations."));
        GenerateSpecification.Generated second = generate.run(sessionId);
        assertThat(second.reused()).isFalse();
        assertThat(second.version().contextHash()).isNotEqualTo(first.contextHash());
    }

    @Test
    void sansContexteEmpreinteDe001() {
        setContext("", CONTEXT_AT);
        llm.drafts.add(GenerateSpecificationTest.valid("Réservation"));
        SpecificationVersion v = generate.run(sessionId).version();
        assertThat(v.contextHash()).isNull();
        assertThat(v.contextUpdatedAt()).isNull();
        assertThat(llm.generationRequests.getFirst().knowledge().isEmpty()).isTrue();
    }

    @Test
    void ficheCompleteeTransmiseCitableEtDansLEmpreinte() {
        llm.drafts.add(citingContext("Le gestionnaire valide les réservations."));
        SpecificationVersion first = generate.run(sessionId).version();

        setContext(CONTEXT + "\n\n" + TECHNICAL, CONTEXT_AT.plusSeconds(120));
        llm.drafts.add(citingContext("Les réservations passent par l'API REST du service salles."));
        GenerateSpecification.Generated second = generate.run(sessionId);
        assertThat(second.reused()).isFalse();
        assertThat(llm.generationRequests.getLast().knowledge().generalSheet()).isEqualTo(CONTEXT + "\n\n" + TECHNICAL);
        assertThat(second.version().contextHash()).isNotEqualTo(first.contextHash());
        assertThat(second.version().contextUpdatedAt()).isEqualTo(CONTEXT_AT.plusSeconds(120));
    }
}
