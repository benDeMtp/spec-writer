package fr.kawamind.application.jira;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.SessionRelationRepository;
import fr.kawamind.domain.session.SessionRelation;
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
