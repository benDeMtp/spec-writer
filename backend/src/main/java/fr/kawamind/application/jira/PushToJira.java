package fr.kawamind.application.jira;

import fr.kawamind.application.knowledge.SessionKnowledge;
import fr.kawamind.application.specification.DraftValidation;
import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.application.specification.ProposalStore;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.ConflictDetector;
import fr.kawamind.domain.jira.ConflictResolution;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.JiraIssue;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.llm.MergeRequest;
import fr.kawamind.domain.llm.QuestionAndAnswer;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.LlmClient;
import fr.kawamind.domain.port.PerimeterRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.port.WikiMarkup;
import fr.kawamind.domain.question.Question;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.session.SpecificationLock;
import fr.kawamind.domain.sources.PerimeterSnapshot;
import fr.kawamind.domain.specification.MarkdownRenderer;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.VersionOrigin;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Envoie la description d'une version vers le ticket lié, après confirmation (FR-047). Jira est
 * relu juste avant l'écriture : s'il a changé depuis la dernière lecture, rien n'est écrit sans
 * un choix explicite (FR-048) : écraser, reprendre la version Jira ou fusionner avec l'IA.
 */
public class PushToJira {

    /** Issue de l'envoi. */
    public sealed interface Outcome {
    }

    /** Envoi fait ({@code OVERWRITE} ou pas de conflit). */
    public record Pushed(LinkedJiraTicket ticket) implements Outcome {
    }

    /** Ticket modifié dans Jira : rien n'a été écrit, l'utilisateur doit choisir. */
    public record Conflict(String jiraDescriptionMarkdown, String localMarkdown, Instant jiraUpdated) implements Outcome {
    }

    /** {@code TAKE_JIRA} : la version Jira est devenue une version locale. */
    public record TakenFromJira(SpecificationVersion version, LinkedJiraTicket ticket) implements Outcome {
    }

    /** {@code AI_MERGE} : une fusion est proposée, à valider avec {@code AcceptAiEdit}. */
    public record MergeProposed(ProposalStore.Proposal proposal) implements Outcome {
    }

    private final IssueTracker tracker;
    private final JiraAccess jira;
    private final WikiMarkup markup;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final ProposalStore proposals;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final SessionKnowledge knowledge;
    private final OperationRegistry operations;
    /** Périmètre repris de la discussion (013, US5) : la version reprise de Jira le garde. */
    private final PerimeterRepository perimeters;

    public PushToJira(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LinkedTicketRepository tickets, ProposalStore proposals, ClockPort clock, IdGenerator ids) {
        this(tracker, jira, markup, llm, consent, sessions, versions, tickets, proposals, clock, ids,
                SessionKnowledge.sheetOnly(projects));
    }

    public PushToJira(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProposalStore proposals, ClockPort clock, IdGenerator ids, SessionKnowledge knowledge) {
        this(tracker, jira, markup, llm, consent, sessions, versions, tickets, proposals, clock, ids, knowledge,
                new OperationRegistry());
    }

    /** {@code operations} : la reprise et la fusion appellent l'IA ; l'envoi bloque les suppressions (FR-019 de 002). */
    public PushToJira(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProposalStore proposals, ClockPort clock, IdGenerator ids, SessionKnowledge knowledge,
            OperationRegistry operations) {
        this(tracker, jira, markup, llm, consent, sessions, versions, tickets, proposals, clock, ids, knowledge, operations,
                null);
    }

    public PushToJira(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProposalStore proposals, ClockPort clock, IdGenerator ids, SessionKnowledge knowledge,
            OperationRegistry operations, PerimeterRepository perimeters) {
        this.perimeters = perimeters;
        this.operations = operations;
        this.knowledge = knowledge;
        this.tracker = tracker;
        this.jira = jira;
        this.markup = markup;
        this.llm = llm;
        this.consent = consent;
        this.sessions = sessions;
        this.versions = versions;
        this.tickets = tickets;
        this.proposals = proposals;
        this.clock = clock;
        this.ids = ids;
    }

    public Outcome run(UUID sessionId, int versionNumber, boolean confirm, ConflictResolution resolution) {
        OperationRegistry.Operation operation = operations.begin(sessionId);
        try {
            return push(sessionId, versionNumber, confirm, resolution);
        } finally {
            operation.close();
        }
    }

    private Outcome push(UUID sessionId, int versionNumber, boolean confirm, ConflictResolution resolution) {
        if (!confirm) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "L'envoi vers Jira doit être confirmé.");
        }
        JiraAccess.Target target = jira.require();
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationLock.requireEditable(sessions, sessionId);
        LinkedJiraTicket ticket = tickets.findBySession(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette session n'est liée à aucun ticket."));
        SpecificationVersion version = versions.find(sessionId, versionNumber)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));

        JiraIssue current = GetJiraIssue.read(tracker, target, ticket.issueKey());
        boolean changed = ConflictDetector.changedSince(ticket, current.updated(), current.description());
        if (!changed) {
            return push(target, ticket, version);
        }
        if (resolution == null) {
            tickets.save(ticket.conflict());
            return new Conflict(markup.toMarkdown(current.description(), ticket.issueUrl()), version.markdown(), current.updated());
        }
        return switch (resolution) {
            case OVERWRITE -> push(target, ticket, version);
            case TAKE_JIRA -> takeJira(session, ticket, current);
            case AI_MERGE -> merge(session, version, current, ticket.issueUrl());
        };
    }

    private Pushed push(JiraAccess.Target target, LinkedJiraTicket ticket, SpecificationVersion version) {
        try {
            tracker.updateDescription(target.endpoint(), ticket.issueKey(), markup.toWiki(version.markdown()));
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
        // Jira date et peut normaliser la description : la référence est ce qu'il renvoie après l'écriture
        JiraIssue written = GetJiraIssue.read(tracker, target, ticket.issueKey());
        LinkedJiraTicket pushed = ticket.pushed(version.number(), written.updated(),
                ConflictDetector.hash(written.description()), clock.now());
        tickets.save(pushed);
        return new Pushed(pushed);
    }

    private TakenFromJira takeJira(Session session, LinkedJiraTicket ticket, JiraIssue current) {
        consent.requireGranted(ConsentService.COPILOT);
        ProjectKnowledge used = knowledge.forSpecification(session, List.of());
        SpecificationContent content = ImportJiraIssue.structure(llm, markup.toMarkdown(current.description(), ticket.issueUrl()),
                session.language(), used);
        PerimeterSnapshot perimeter = perimeters == null ? null : perimeters.snapshot(session.id()).orElse(null);
        if (perimeter != null) {
            content = perimeter.withAssumptions(content, session.language());
        }
        int number = versions.nextNumber(session.id());
        SpecificationVersion taken = new SpecificationVersion(ids.next(), session.id(), number, content,
                "jira-import:" + current.key() + ":" + current.updated(), VersionOrigin.JIRA_IMPORT,
                MarkdownRenderer.render(content, session.language(), used.selection(), perimeter), clock.now())
                .withKnowledge(used);
        // la feature a pu passer à « implémentée » pendant l'appel au LLM (research.md R7)
        SpecificationLock.requireEditable(sessions, session.id());
        versions.save(taken);
        sessions.save(session.generated(clock.now()), sessions.questionnaire(session.id()).orElseGet(Questionnaire::empty));
        LinkedJiraTicket reimported = ticket.reimported(number, current.updated(), ConflictDetector.hash(current.description()));
        tickets.save(reimported);
        return new TakenFromJira(taken, reimported);
    }

    private MergeProposed merge(Session session, SpecificationVersion local, JiraIssue current, String issueUrl) {
        consent.requireGranted(ConsentService.COPILOT);
        List<Question> treated = sessions.questionnaire(session.id()).map(Questionnaire::treated).orElse(List.of());
        List<QuestionAndAnswer> answers = DraftValidation.answers(treated);
        ProjectKnowledge used = knowledge.forSpecification(session, answers);
        MergeRequest request = new MergeRequest(session.need().text(), session.language(), answers, local.markdown(),
                markup.toMarkdown(current.description(), issueUrl), null, used);
        SpecificationContent content = DraftValidation.produce(c -> llm.mergeConflict(request.withCorrection(c)), treated,
                session.language(), ErrorCode.LLM_INVALID_OUTPUT, used);
        return new MergeProposed(proposals.propose(session, local, content, VersionOrigin.CONFLICT_MERGE, current.updated(),
                ConflictDetector.hash(current.description()), used));
    }
}
