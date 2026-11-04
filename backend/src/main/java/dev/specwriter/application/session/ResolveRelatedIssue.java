package dev.specwriter.application.session;

import dev.specwriter.application.jira.ImportJiraIssue;
import dev.specwriter.application.knowledge.RelatedIssues;
import dev.specwriter.application.knowledge.RelatedIssues.Decision;
import dev.specwriter.application.knowledge.RelatedIssuesStore;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.application.specification.ProposeAiEdit;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.ProjectContextInput;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRelationRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionRelation;
import java.util.UUID;

/**
 * Choix de l'utilisateur devant les tickets proches (FR-035, research.md R23) :
 * <ul>
 * <li>{@code LINK_NEW} (ticket fermé) : la nouvelle story sera liée au ticket à sa création ;</li>
 * <li>{@code SAME} (ticket non fermé) : la session est remplacée par l'import du ticket, et le
 * besoin est proposé comme modification assistée ;</li>
 * <li>{@code DIFFERENT}, {@code NONE} : la rédaction continue.</li>
 * </ul>
 * Les questions de clarification ne sont demandées qu'après ce choix.
 */
public class ResolveRelatedIssue {

    /** Session qui continue (ou qui remplace celle-ci), ticket lié, et proposition pour {@code SAME}. */
    public record Outcome(UUID sessionId, String relatesTo, ProposalStore.Proposal proposal) {
    }

    static final int MAX_INSTRUCTION = 2_000;

    private final SessionRepository sessions;
    private final ProjectRepository projects;
    private final RelatedIssuesStore related;
    private final SessionRelationRepository relations;
    private final ImportJiraIssue importIssue;
    private final ProposeAiEdit proposeEdit;
    private final ManageSessions manageSessions;
    private final LlmClient llm;
    private final ClockPort clock;
    private final IdGenerator ids;

    public ResolveRelatedIssue(SessionRepository sessions, ProjectRepository projects, RelatedIssuesStore related,
            SessionRelationRepository relations, ImportJiraIssue importIssue, ProposeAiEdit proposeEdit,
            ManageSessions manageSessions, LlmClient llm, ClockPort clock, IdGenerator ids) {
        this.sessions = sessions;
        this.projects = projects;
        this.related = related;
        this.relations = relations;
        this.importIssue = importIssue;
        this.proposeEdit = proposeEdit;
        this.manageSessions = manageSessions;
        this.llm = llm;
        this.clock = clock;
        this.ids = ids;
    }

    public Outcome run(UUID sessionId, Decision decision, String key) {
        if (decision == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez LINK_NEW, SAME, DIFFERENT ou NONE.");
        }
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        RelatedIssues current = related.find(sessionId)
                .orElse(new RelatedIssues(RelatedIssues.Status.UNAVAILABLE, java.util.List.of(), null));
        RelatedIssues.Issue issue = decision == Decision.NONE ? null : current.issues().stream()
                .filter(i -> i.key().equals(key)).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_FAILED,
                        "Choisissez un ticket parmi les tickets proches."));
        return switch (decision) {
            case LINK_NEW -> {
                if (!issue.closed()) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "Ce ticket n'est pas fermé : comparez-le au besoin avant de décider.");
                }
                relations.save(new SessionRelation(sessionId, issue.key(), SessionRelation.Link.PENDING));
                yield proceed(session, current.decided(decision), issue.key());
            }
            case SAME -> {
                if (issue.closed()) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "Ce ticket est fermé : rédigez une nouvelle story liée à lui.");
                }
                yield replaceByIssue(session, issue.key());
            }
            case DIFFERENT, NONE -> proceed(session, current.decided(decision), null);
        };
    }

    /** La rédaction continue : premier lot de questions, s'il n'a pas encore été demandé. */
    private Outcome proceed(Session session, RelatedIssues decided, String relatesTo) {
        related.save(session.id(), decided);
        Questionnaire questionnaire = sessions.questionnaire(session.id()).orElse(Questionnaire.empty());
        if (questionnaire.questions().isEmpty()) {
            Project project = projects.find(session.projectId())
                    .orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
            questionnaire = NextBatch.ask(llm, ids, session, questionnaire, ProjectContextInput.of(project.contexts()));
            Session updated = questionnaire.pending().isEmpty() ? session.readyToGenerate(clock.now()) : session;
            sessions.save(updated, questionnaire);
        }
        return new Outcome(session.id(), relatesTo, null);
    }

    /**
     * Import du ticket, puis le besoin proposé comme modification assistée ; la session du besoin
     * n'est supprimée qu'une fois la proposition obtenue.
     */
    private Outcome replaceByIssue(Session session, String key) {
        ImportJiraIssue.Imported imported = importIssue.run(key);
        String need = session.need().text();
        String instruction = need.length() <= MAX_INSTRUCTION ? need : need.substring(0, MAX_INSTRUCTION);
        ProposalStore.Proposal proposal = proposeEdit.run(imported.session().id(), instruction);
        manageSessions.delete(session.id());
        related.discard(session.id());
        return new Outcome(imported.session().id(), null, proposal);
    }
}
