package dev.specwriter.application.jira;

import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.project.Project;
import dev.specwriter.application.project.CurrentProject;
import dev.specwriter.application.specification.DraftValidation;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.ConflictDetector;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.llm.StructureImportRequest;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import java.util.List;
import java.util.Optional;

/**
 * Importe un ticket Jira en session liée (FR-046, US6-5) : le besoin est la description convertie en
 * Markdown, la première version est structurée par le LLM. La session est créée dans le projet
 * courant (FR-004 de 002). Jira n'est jamais modifié. En cas d'échec, rien n'est enregistré.
 */
public class ImportJiraIssue {

    /** {@code existing} : le ticket était déjà lié ; c'est sa spécification, rien n'est importé (011, US4). */
    public record Imported(Session session, SpecificationVersion version, LinkedJiraTicket ticket, boolean truncated,
            boolean existing) {

        public Imported(Session session, SpecificationVersion version, LinkedJiraTicket ticket, boolean truncated) {
            this(session, version, ticket, truncated, false);
        }
    }

    private final IssueTracker tracker;
    private final JiraAccess jira;
    private final WikiMarkup markup;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final SessionRepository sessions;
    private final ProjectRepository projects;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final SessionKnowledge knowledge;

    public ImportJiraIssue(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock, IdGenerator ids) {
        this(tracker, jira, markup, llm, consent, sessions, projects, versions, tickets, clock, ids,
                SessionKnowledge.sheetOnly(projects));
    }

    public ImportJiraIssue(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock, IdGenerator ids, SessionKnowledge knowledge) {
        this.knowledge = knowledge;
        this.tracker = tracker;
        this.jira = jira;
        this.markup = markup;
        this.llm = llm;
        this.consent = consent;
        this.sessions = sessions;
        this.projects = projects;
        this.versions = versions;
        this.tickets = tickets;
        this.clock = clock;
        this.ids = ids;
    }

    public Imported run(String issueKey) {
        JiraAccess.Target target = jira.require();
        Optional<Imported> linked = tickets.findByIssueKey(issueKey).flatMap(this::existing);
        if (linked.isPresent()) {
            return linked.get();
        }
        JiraIssue issue = GetJiraIssue.read(tracker, target, issueKey);
        consent.requireGranted(ConsentService.COPILOT);

        String text = markup.toMarkdown(issue.description(), target.issueUrl(issue.key())).strip();
        boolean truncated = text.length() > Need.MAX_LENGTH;
        Need need = need(truncated ? text.substring(0, Need.MAX_LENGTH) : text, issue.summary());
        Project project = CurrentProject.resolve(projects);
        Session session = Session.imported(ids.next(), project.id(), need, issue.summary(), clock.now());
        ProjectKnowledge used = knowledge.forSpecification(session, List.of());
        SpecificationContent content = structure(llm, need.text(), session.language(), used);

        SpecificationVersion version = new SpecificationVersion(ids.next(), session.id(), 1, content,
                "jira-import:" + issue.key() + ":" + issue.updated(), VersionOrigin.JIRA_IMPORT,
                MarkdownRenderer.render(content, session.language(), used.selection()), clock.now()).withKnowledge(used);
        LinkedJiraTicket ticket = new LinkedJiraTicket(session.id(), issue.key(), target.issueUrl(issue.key()), null, 1,
                issue.updated(), ConflictDetector.hash(issue.description()), clock.now(), null,
                LinkedJiraTicket.SyncState.IN_SYNC);
        sessions.save(session, Questionnaire.empty());
        versions.save(version);
        tickets.save(ticket);
        return new Imported(session, version, ticket, truncated);
    }

    /** Ticket déjà lié : sa spécification et sa dernière version, pour en discuter (011, US4). */
    private Optional<Imported> existing(LinkedJiraTicket ticket) {
        return sessions.find(ticket.sessionId()).flatMap(s -> versions.list(s.id()).stream()
                .max(java.util.Comparator.comparingInt(SpecificationVersion::number))
                .map(v -> new Imported(s, v, ticket, false, true)));
    }

    /** La description ; trop courte pour un besoin, elle est précédée du titre du ticket. */
    private static Need need(String description, String summary) {
        try {
            return Need.of(description);
        } catch (DomainException tooShort) {
            try {
                return Need.of((summary == null ? "" : summary.strip() + "\n\n") + description);
            } catch (DomainException stillTooShort) {
                throw new DomainException(ErrorCode.IMPORT_UNSTRUCTURABLE, "La description du ticket est trop courte.");
            }
        }
    }

    /** Structuration d'un texte importé ; un texte non structurable donne {@code IMPORT_UNSTRUCTURABLE}. */
    static SpecificationContent structure(LlmClient llm, String text, Language language,
            ProjectKnowledge knowledge) {
        StructureImportRequest request = new StructureImportRequest(text, language, null, knowledge);
        return DraftValidation.produce(c -> llm.structureImportedText(request.withCorrection(c)), List.of(), language,
                ErrorCode.IMPORT_UNSTRUCTURABLE, knowledge);
    }
}
