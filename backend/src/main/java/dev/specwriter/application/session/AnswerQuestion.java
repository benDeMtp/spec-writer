package dev.specwriter.application.session;

import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.question.Answer;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SpecificationLock;
import dev.specwriter.domain.session.SessionState;
import java.util.List;
import java.util.UUID;

/**
 * Répond à une question, la passe, ou modifie une réponse antérieure (FR-006, FR-007). La réponse
 * est sauvegardée avant tout appel au LLM (FR-016) : si le LLM échoue, elle est conservée
 * (FR-023a) et il suffit de la renvoyer pour réessayer.
 */
public class AnswerQuestion {

    private final SessionRepository sessions;
    private final SessionKnowledge knowledge;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final ReviseAnswer revise;

    public AnswerQuestion(SessionRepository sessions, ProjectRepository projects, LlmClient llm, ConsentPolicy consent,
            ClockPort clock, IdGenerator ids) {
        this(sessions, llm, consent, clock, ids, SessionKnowledge.sheetOnly(projects));
    }

    public AnswerQuestion(SessionRepository sessions, LlmClient llm, ConsentPolicy consent, ClockPort clock,
            IdGenerator ids, SessionKnowledge knowledge) {
        this.sessions = sessions;
        this.knowledge = knowledge;
        this.llm = llm;
        this.consent = consent;
        this.clock = clock;
        this.ids = ids;
        this.revise = new ReviseAnswer(llm, ids);
    }

    /** {@code value} vide et {@code skip} vrai pour passer la question. */
    public SessionSnapshot run(UUID sessionId, UUID questionId, String value, boolean skip) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationLock.requireEditable(sessions, sessionId);
        Questionnaire before = sessions.questionnaire(sessionId).orElseThrow();
        Answer previous = before.question(questionId).map(Question::answer).orElse(null);
        Questionnaire questionnaire = skip ? before.skip(questionId, clock.now()) : before.answer(questionId, value, clock.now());
        Answer current = questionnaire.question(questionId).orElseThrow().answer();
        boolean revised = previous != null
                && (!previous.value().equals(current.value()) || previous.assumption() != current.assumption());
        if (previous != null && !revised) {
            // même réponse : rien ne change, sauf s'il faut relancer le calcul du lot suivant
            questionnaire = before;
        } else {
            session = session.answerChanged(clock.now());
            sessions.save(session, questionnaire);
        }

        Revision revision = Revision.NONE;
        if (revised) {
            consent.requireGranted(ConsentService.COPILOT);
            ReviseAnswer.Result result = revise.reassess(session, questionnaire, questionId,
                    knowledge.forQuestions(session, NextBatch.known(questionnaire)));
            questionnaire = result.questionnaire();
            revision = result.revision();
            if (questionnaire.pending().isEmpty()) {
                session = session.readyToGenerate(clock.now());
            }
            sessions.save(session, questionnaire);
        } else if (questionnaire.pending().isEmpty() && session.state() == SessionState.QUESTIONING) {
            consent.requireGranted(ConsentService.COPILOT);
            questionnaire = NextBatch.ask(llm, ids, session, questionnaire, knowledge);
            if (questionnaire.pending().isEmpty()) {
                session = session.readyToGenerate(clock.now());
            }
            sessions.save(session, questionnaire);
        }
        return new SessionSnapshot(session, questionnaire, List.of(), revision);
    }
}
