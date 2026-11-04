package dev.specwriter.application.session;

import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.domain.project.Project;
import dev.specwriter.application.knowledge.FindRelatedIssues;
import dev.specwriter.application.knowledge.RelatedIssues;
import dev.specwriter.application.knowledge.RelatedIssuesStore;
import dev.specwriter.application.project.CurrentProject;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import java.util.List;

/**
 * Crée une session à partir d'un besoin, dans le projet courant (FR-004 de 002), et calcule le
 * premier lot de questions (FR-001, FR-002), sauf si des tickets proches attendent le choix de
 * l'utilisateur (FR-034 de 002).
 * Le besoin est validé avant tout envoi ; rien n'est envoyé sans consentement (FR-024). Si le LLM
 * échoue, aucune session n'est créée et le texte reste dans le formulaire.
 */
public class StartSession {

    private final SessionRepository sessions;
    private final ProjectRepository projects;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final FindRelatedIssues findRelated;
    private final RelatedIssuesStore related;
    private final SessionKnowledge knowledge;

    public StartSession(SessionRepository sessions, ProjectRepository projects, LlmClient llm, ConsentPolicy consent,
            ClockPort clock, IdGenerator ids) {
        this(sessions, projects, llm, consent, clock, ids, null, null);
    }

    public StartSession(SessionRepository sessions, ProjectRepository projects, LlmClient llm, ConsentPolicy consent,
            ClockPort clock, IdGenerator ids, FindRelatedIssues findRelated, RelatedIssuesStore related) {
        this(sessions, projects, llm, consent, clock, ids, findRelated, related, SessionKnowledge.sheetOnly(projects));
    }

    /**
     * Avec les tickets proches (FR-034) : s'il y en a, les questions attendent le choix de
     * l'utilisateur ; et avec la connaissance du projet (research.md R24).
     */
    public StartSession(SessionRepository sessions, ProjectRepository projects, LlmClient llm, ConsentPolicy consent,
            ClockPort clock, IdGenerator ids, FindRelatedIssues findRelated, RelatedIssuesStore related,
            SessionKnowledge knowledge) {
        this.knowledge = knowledge;
        this.sessions = sessions;
        this.projects = projects;
        this.llm = llm;
        this.consent = consent;
        this.clock = clock;
        this.ids = ids;
        this.findRelated = findRelated;
        this.related = related;
    }

    public SessionSnapshot run(String needText, String name) {
        Need need = Need.of(needText);
        Project project = CurrentProject.resolve(projects);
        Session session = Session.start(ids.next(), project.id(), need, name, clock.now());
        consent.requireGranted(ConsentService.COPILOT);
        if (findRelated != null) {
            RelatedIssues found = findRelated.find(session, project);
            related.save(session.id(), found);
            if (found.awaitingDecision()) {
                sessions.save(session, Questionnaire.empty());
                return new SessionSnapshot(session, Questionnaire.empty(), List.of());
            }
        }
        Questionnaire questionnaire = NextBatch.ask(llm, ids, session, Questionnaire.empty(), knowledge);
        if (questionnaire.pending().isEmpty()) {
            session = session.readyToGenerate(clock.now());
        }
        sessions.save(session, questionnaire);
        return new SessionSnapshot(session, questionnaire, List.of());
    }
}
