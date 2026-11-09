package dev.specwriter.application.session;

import dev.specwriter.application.attachment.AttachmentDrafts;
import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.domain.project.Project;
import dev.specwriter.application.knowledge.FindRelatedIssues;
import dev.specwriter.application.knowledge.RelatedIssues;
import dev.specwriter.application.knowledge.RelatedIssuesStore;
import dev.specwriter.application.project.CurrentProject;
import dev.specwriter.domain.attachment.AttachmentDraft;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.port.AttachmentRepository;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.conversation.EstablishedPoint;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.question.Answer;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.QuestionState;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Crée une session à partir d'un besoin, dans le projet courant (FR-004 de 002), et calcule le
 * premier lot de questions (FR-001, FR-002), sauf si des tickets proches attendent le choix de
 * l'utilisateur (FR-034 de 002).
 * Le besoin est validé avant tout envoi ; rien n'est envoyé sans consentement (FR-024). Si le LLM
 * échoue, aucune session n'est créée et le texte reste dans le formulaire.
 */
public class StartSession {

    /** Raison des questions tirées d'une conversation. */
    public static final String ESTABLISHED = "Établi dans la conversation";

    private final SessionRepository sessions;
    private final ProjectRepository projects;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final FindRelatedIssues findRelated;
    private final RelatedIssuesStore related;
    private final SessionKnowledge knowledge;
    /** Documents joints sur l'écran du besoin (009) ; absents, le démarrage est celui d'avant. */
    private final AttachmentDrafts drafts;
    private final AttachmentRepository attachments;

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
        this(sessions, projects, llm, consent, clock, ids, findRelated, related, knowledge, null, null);
    }

    /** Avec les documents joints avant le démarrage (009, research.md R3). */
    public StartSession(SessionRepository sessions, ProjectRepository projects, LlmClient llm, ConsentPolicy consent,
            ClockPort clock, IdGenerator ids, FindRelatedIssues findRelated, RelatedIssuesStore related,
            SessionKnowledge knowledge, AttachmentDrafts drafts, AttachmentRepository attachments) {
        this.drafts = drafts;
        this.attachments = attachments;
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
        return run(needText, name, CurrentProject.resolve(projects));
    }

    /** Avec les documents joints sur l'écran du besoin, dans l'ordre d'ajout (009). */
    public SessionSnapshot run(String needText, String name, List<UUID> attachmentIds) {
        return run(needText, name, CurrentProject.resolve(projects), attachmentIds);
    }

    /** Dans un projet donné : une évolution reste dans le projet de sa spécification d'origine. */
    public SessionSnapshot run(String needText, String name, Project project) {
        return run(needText, name, project, List.of());
    }

    /**
     * Les documents joints sont rangés avec la session avant le premier lot, qui les reçoit ; si le
     * démarrage échoue, la session et ses documents sont supprimés, et les brouillons restent.
     */
    public SessionSnapshot run(String needText, String name, Project project, List<UUID> attachmentIds) {
        Need need = Need.of(needText);
        List<AttachmentDraft> joined = drafts == null ? List.of() : drafts.get(attachmentIds);
        Session session = Session.start(ids.next(), project.id(), need, name, clock.now());
        consent.requireGranted(ConsentService.COPILOT);
        if (joined.isEmpty()) {
            return start(session, project);
        }
        sessions.save(session, Questionnaire.empty());
        for (int i = 0; i < joined.size(); i++) {
            attachments.add(joined.get(i).attach(session.id(), i + 1, clock.now()));
        }
        SessionSnapshot started;
        try {
            started = start(session, project);
        } catch (RuntimeException e) {
            sessions.delete(session.id());
            throw e;
        }
        drafts.forget(attachmentIds);
        return started;
    }

    /**
     * Depuis une conversation (010, US2) : les points établis relus par l'utilisateur sont des
     * réponses déjà données (lot 0), que le premier lot ne repose pas ; les documents joints à la
     * conversation suivent la spécification. Si le démarrage échoue, rien n'est gardé.
     */
    public SessionSnapshot fromConversation(String needText, List<EstablishedPoint> points, Project project,
            List<AttachmentDraft> joined) {
        Need need = Need.of(needText);
        if (points.size() > NeedDraft.MAX_POINTS) {
            throw new DomainException(ErrorCode.NEED_DRAFT_INVALID, "10 points établis au plus.");
        }
        Session session = Session.start(ids.next(), project.id(), need, null, clock.now());
        consent.requireGranted(ConsentService.COPILOT);
        Questionnaire established = established(points);
        sessions.save(session, established);
        try {
            for (int i = 0; i < joined.size(); i++) {
                attachments.add(joined.get(i).attach(session.id(), i + 1, clock.now()));
            }
            return start(session, project, established);
        } catch (RuntimeException e) {
            sessions.delete(session.id());
            throw e;
        }
    }

    private Questionnaire established(List<EstablishedPoint> points) {
        List<Question> answered = new ArrayList<>();
        for (EstablishedPoint p : points) {
            UUID id = ids.next();
            answered.add(new Question(id, answered.size() + 1, p.theme(), 0, p.question(), ESTABLISHED, List.of(), null,
                    List.of(), QuestionState.ANSWERED, new Answer(id, p.answer(), false, clock.now())));
        }
        return new Questionnaire(answered, Set.of());
    }

    private SessionSnapshot start(Session session, Project project) {
        return start(session, project, Questionnaire.empty());
    }

    private SessionSnapshot start(Session session, Project project, Questionnaire initial) {
        if (findRelated != null) {
            RelatedIssues found = findRelated.find(session, project);
            related.save(session.id(), found);
            if (found.awaitingDecision()) {
                sessions.save(session, initial);
                return new SessionSnapshot(session, initial, List.of());
            }
        }
        Questionnaire questionnaire = NextBatch.ask(llm, ids, session, initial, knowledge);
        if (questionnaire.pending().isEmpty()) {
            session = session.readyToGenerate(clock.now());
        }
        sessions.save(session, questionnaire);
        return new SessionSnapshot(session, questionnaire, List.of());
    }
}
