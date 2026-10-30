package dev.specwriter.application.session;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import java.util.List;
import java.util.UUID;

/**
 * Répond à une question ou la passe (FR-006). La réponse est sauvegardée avant tout appel au LLM
 * (FR-016) : si le lot suivant ne peut pas être calculé, elle est conservée (FR-023a) et il suffit
 * de renvoyer la réponse pour réessayer.
 */
public class AnswerQuestion {

    private final SessionRepository sessions;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;

    public AnswerQuestion(SessionRepository sessions, LlmClient llm, ConsentPolicy consent, ClockPort clock,
            IdGenerator ids) {
        this.sessions = sessions;
        this.llm = llm;
        this.consent = consent;
        this.clock = clock;
        this.ids = ids;
    }

    /** {@code value} vide et {@code skip} vrai pour passer la question. */
    public SessionSnapshot run(UUID sessionId, UUID questionId, String value, boolean skip) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Questionnaire questionnaire = sessions.questionnaire(sessionId).orElseThrow();
        questionnaire = skip ? questionnaire.skip(questionId, clock.now()) : questionnaire.answer(questionId, value, clock.now());
        session = session.answerChanged(clock.now());
        sessions.save(session, questionnaire);

        if (questionnaire.pending().isEmpty()) {
            consent.requireGranted(ConsentService.COPILOT);
            questionnaire = NextBatch.ask(llm, ids, session, questionnaire);
            if (questionnaire.pending().isEmpty()) {
                session = session.readyToGenerate(clock.now());
            }
            sessions.save(session, questionnaire);
        }
        return new SessionSnapshot(session, questionnaire, List.of());
    }
}
