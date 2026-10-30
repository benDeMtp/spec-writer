package dev.specwriter.application.jira;

import dev.specwriter.application.specification.DraftValidation;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.ConflictDetector;
import dev.specwriter.domain.jira.ConflictResolution;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.llm.MergeRequest;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
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

    public PushToJira(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProposalStore proposals, ClockPort clock, IdGenerator ids) {
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
        if (!confirm) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "L'envoi vers Jira doit être confirmé.");
        }
        JiraAccess.Target target = jira.require();
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
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
            return new Conflict(markup.toMarkdown(current.description()), version.markdown(), current.updated());
        }
        return switch (resolution) {
            case OVERWRITE -> push(target, ticket, version);
            case TAKE_JIRA -> takeJira(session, ticket, current);
            case AI_MERGE -> merge(session, version, current);
        };
    }

    private Pushed push(JiraAccess.Target target, LinkedJiraTicket ticket, SpecificationVersion version) {
        try {
            tracker.updateDescription(target.endpoint(), ticket.issueKey(), markup.toWiki(version.markdown()));
        } catch (IssueTrackerException e) {
            throw new DomainException(e.errorCode());
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
        SpecificationContent content = ImportJiraIssue.structure(llm, markup.toMarkdown(current.description()),
                session.language());
        int number = versions.lastNumber(session.id()) + 1;
        SpecificationVersion taken = new SpecificationVersion(ids.next(), session.id(), number, content,
                "jira-import:" + current.key() + ":" + current.updated(), VersionOrigin.JIRA_IMPORT,
                MarkdownRenderer.render(content, session.language()), clock.now());
        versions.save(taken);
        sessions.save(session.generated(clock.now()), sessions.questionnaire(session.id()).orElseGet(Questionnaire::empty));
        LinkedJiraTicket reimported = ticket.reimported(number, current.updated(), ConflictDetector.hash(current.description()));
        tickets.save(reimported);
        return new TakenFromJira(taken, reimported);
    }

    private MergeProposed merge(Session session, SpecificationVersion local, JiraIssue current) {
        consent.requireGranted(ConsentService.COPILOT);
        List<Question> treated = sessions.questionnaire(session.id()).map(Questionnaire::treated).orElse(List.of());
        MergeRequest request = new MergeRequest(session.need().text(), session.language(), DraftValidation.answers(treated),
                local.markdown(), markup.toMarkdown(current.description()), null);
        SpecificationContent content = DraftValidation.produce(c -> llm.mergeConflict(request.withCorrection(c)), treated,
                session.language(), ErrorCode.LLM_INVALID_OUTPUT);
        return new MergeProposed(proposals.propose(session, local, content, VersionOrigin.CONFLICT_MERGE, current.updated(),
                ConflictDetector.hash(current.description())));
    }
}
