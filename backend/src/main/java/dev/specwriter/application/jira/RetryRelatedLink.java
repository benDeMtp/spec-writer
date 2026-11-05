package dev.specwriter.application.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SessionRelationRepository;
import dev.specwriter.domain.session.SessionRelation;
import java.util.UUID;

/** Réessaie le lien « relates to » d'une story déjà créée (FR-035 de 002, research.md R23). */
public class RetryRelatedLink {

    private final IssueTracker tracker;
    private final JiraAccess jira;
    private final LinkedTicketRepository tickets;
    private final SessionRelationRepository relations;

    public RetryRelatedLink(IssueTracker tracker, JiraAccess jira, LinkedTicketRepository tickets,
            SessionRelationRepository relations) {
        this.tracker = tracker;
        this.jira = jira;
        this.tickets = tickets;
        this.relations = relations;
    }

    public SessionRelation run(UUID sessionId) {
        LinkedJiraTicket story = tickets.findBySession(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "La story n'a pas encore été créée."));
        SessionRelation relation = relations.find(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Aucun ticket à lier."));
        if (relation.link() == SessionRelation.Link.LINKED) {
            return relation;
        }
        return CreateJiraTicket.link(tracker, relations, jira.require(sessionId), story.issueKey(), relation);
    }
}
