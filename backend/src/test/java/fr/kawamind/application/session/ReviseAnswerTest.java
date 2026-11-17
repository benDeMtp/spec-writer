package fr.kawamind.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.llm.ProposedQuestion;
import fr.kawamind.domain.llm.ReassessResult;
import fr.kawamind.domain.question.Question;
import fr.kawamind.domain.question.QuestionState;
import fr.kawamind.domain.question.QuestionTheme;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.SessionState;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryConsents;
import fr.kawamind.support.InMemoryProjects;
import fr.kawamind.support.InMemorySessions;
import fr.kawamind.support.SequentialIdGenerator;
import fr.kawamind.support.StubLlm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Modification d'une réponse antérieure (FR-007, US3-2). */
class ReviseAnswerTest {

    final InMemorySessions sessions = new InMemorySessions();
    final InMemoryConsents consents = new InMemoryConsents();
    final StubLlm llm = new StubLlm();
    final FixedClock clock = new FixedClock();
    final SequentialIdGenerator ids = new SequentialIdGenerator();
    final ConsentPolicy policy = new ConsentPolicy(consents);
    AnswerQuestion answer;
    UUID sessionId;
    List<UUID> q;

    @BeforeEach
    void setUp() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        llm.questions.add(SessionUseCasesTest.batch(QuestionTheme.GOAL, QuestionTheme.ERRORS, QuestionTheme.EDGE_CASES));
        SessionSnapshot s = new StartSession(sessions, new InMemoryProjects(), llm, policy, clock, ids)
                .run(SessionUseCasesTest.NEED, null);
        sessionId = s.session().id();
        q = s.questionnaire().currentBatch().stream().map(Question::id).toList();
        answer = new AnswerQuestion(sessions, new InMemoryProjects(), llm, policy, clock, ids);
        answer.run(sessionId, q.get(0), "Gagner du temps", false);
        answer.run(sessionId, q.get(1), "", true);
        llm.questions.add(StubLlm.none());
        answer.run(sessionId, q.get(2), "Refuser", false);
        assertThat(sessions.find(sessionId).orElseThrow().state()).isEqualTo(SessionState.READY_TO_GENERATE);
    }

    Questionnaire questionnaire() {
        return sessions.questionnaire(sessionId).orElseThrow();
    }

    @Test
    void reponseModifieeQuestionsObsoletesRetireesEtNouvellesAjoutees() {
        llm.reassessments.add(new ReassessResult(List.of(q.get(2).toString(), q.get(0).toString(), "inconnue"),
                List.of(new ProposedQuestion(QuestionTheme.BUSINESS_RULES, "Combien de salles ?", "Nouvelle règle", List.of(),
                        null, List.of()))));
        SessionSnapshot s = answer.run(sessionId, q.get(0), "Éviter les doublons", false);

        assertThat(llm.reassessRequests).singleElement().satisfies(r -> {
            assertThat(r.changedQuestionId()).isEqualTo(q.get(0).toString());
            assertThat(r.known()).anySatisfy(k -> assertThat(k.answer()).isEqualTo("Éviter les doublons"));
        });
        // la question modifiée n'est jamais retirée ; l'identifiant inconnu est ignoré
        assertThat(questionnaire().question(q.get(0)).orElseThrow().answer().value()).isEqualTo("Éviter les doublons");
        assertThat(questionnaire().question(q.get(2)).orElseThrow().state()).isEqualTo(QuestionState.OBSOLETE);
        assertThat(questionnaire().question(q.get(1)).orElseThrow().state()).isEqualTo(QuestionState.SKIPPED);
        assertThat(s.questionnaire().currentBatch()).extracting(Question::label).containsExactly("Combien de salles ?");
        assertThat(s.revision().removed()).containsExactly("Question EDGE_CASES ?");
        assertThat(s.revision().added()).containsExactly("Combien de salles ?");
        assertThat(s.session().state()).isEqualTo(SessionState.QUESTIONING);
        assertThat(s.questionnaire().active()).hasSize(3);
    }

    @Test
    void sessionGenereeRepasseEnQuestionnaire() {
        var session = sessions.find(sessionId).orElseThrow();
        sessions.save(session.generated(clock.now()), questionnaire());
        llm.reassessments.add(new ReassessResult(List.of(), List.of(new ProposedQuestion(QuestionTheme.NON_FUNCTIONAL,
                "Quel délai de réponse ?", "Performance", List.of(), "1 seconde", List.of()))));
        assertThat(answer.run(sessionId, q.get(1), "Mieux remplir", false).session().state())
                .isEqualTo(SessionState.QUESTIONING);
    }

    @Test
    void sansNouvelleQuestionLaSessionResteGenerable() {
        llm.reassessments.add(new ReassessResult(List.of(), List.of()));
        SessionSnapshot s = answer.run(sessionId, q.get(1), "Une vraie réponse", false);
        assertThat(s.session().state()).isEqualTo(SessionState.READY_TO_GENERATE);
        assertThat(s.revision()).isEqualTo(Revision.NONE);
        assertThat(questionnaire().question(q.get(1)).orElseThrow().state()).isEqualTo(QuestionState.ANSWERED);
    }

    @Test
    void reponseIdentiqueAucunAppel() {
        SessionSnapshot s = answer.run(sessionId, q.get(0), "Gagner du temps", false);
        assertThat(llm.reassessRequests).isEmpty();
        assertThat(s.session().state()).isEqualTo(SessionState.READY_TO_GENERATE);
    }

    @Test
    void llmIndisponibleLaNouvelleReponseEstConservee() {
        assertThatThrownBy(() -> answer.run(sessionId, q.get(0), "Autre objectif", false))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_UNAVAILABLE));
        assertThat(questionnaire().question(q.get(0)).orElseThrow().answer().value()).isEqualTo("Autre objectif");
        assertThat(sessions.find(sessionId).orElseThrow().state()).isEqualTo(SessionState.QUESTIONING);
    }
}
