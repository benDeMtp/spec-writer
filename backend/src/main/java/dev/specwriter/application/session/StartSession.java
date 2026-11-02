package dev.specwriter.application.session;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import java.util.List;

/**
 * Crée une session à partir d'un besoin et calcule le premier lot de questions (FR-001, FR-002).
 * Le besoin est validé avant tout envoi ; rien n'est envoyé sans consentement (FR-024). Si le LLM
 * échoue, aucune session n'est créée et le texte reste dans le formulaire.
 */
public class StartSession {

    private final SessionRepository sessions;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;

    public StartSession(SessionRepository sessions, LlmClient llm, ConsentPolicy consent, ClockPort clock,
            IdGenerator ids) {
        this.sessions = sessions;
        this.llm = llm;
        this.consent = consent;
        this.clock = clock;
        this.ids = ids;
    }

    public SessionSnapshot run(String needText, String name) {
        Need need = Need.of(needText);
        Session session = Session.start(ids.next(), need, name, clock.now());
        consent.requireGranted(ConsentService.COPILOT);
        Questionnaire questionnaire = NextBatch.ask(llm, ids, session, Questionnaire.empty());
        if (questionnaire.pending().isEmpty()) {
            session = session.readyToGenerate(clock.now());
        }
        sessions.save(session, questionnaire);
        return new SessionSnapshot(session, questionnaire, List.of());
    }
}
