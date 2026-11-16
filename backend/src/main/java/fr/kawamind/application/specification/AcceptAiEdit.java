package fr.kawamind.application.specification;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.session.SpecificationLock;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.VersionOrigin;
import java.util.UUID;

/**
 * Applique une proposition de l'IA (US6-5) : nouvelle version {@code AI_EDIT}, {@code CONFLICT_MERGE}
 * ou {@code DISCUSSION} (011). Jira n'est pas modifié (FR-046) ; un ticket lié passe à {@code LOCAL_AHEAD}.
 */
public class AcceptAiEdit {

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final ProposalStore proposals;
    private final ClockPort clock;
    private final IdGenerator ids;

    public AcceptAiEdit(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProposalStore proposals, ClockPort clock, IdGenerator ids) {
        this.sessions = sessions;
        this.versions = versions;
        this.tickets = tickets;
        this.proposals = proposals;
        this.clock = clock;
        this.ids = ids;
    }

    public SpecificationVersion run(UUID sessionId, UUID proposalId) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationLock.requireEditable(sessions, sessionId);
        ProposalStore.Proposal p = proposals.take(sessionId, proposalId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette proposition n'existe plus."));
        SpecificationVersion version = new SpecificationVersion(ids.next(), sessionId, versions.nextNumber(sessionId),
                p.content(), "proposal:" + p.id(), p.origin(), p.markdown(), clock.now())
                .withKnowledge(p.knowledge());
        versions.save(version);
        proposals.adopt(p, clock.now());
        if (p.origin() == VersionOrigin.DISCUSSION && p.conversationId() != null) {
            // « Issue de la discussion », avec le lien vers la conversation (011, FR-011)
            versions.linkDiscussion(version.id(), p.conversationId());
        }
        Questionnaire questionnaire = sessions.questionnaire(sessionId).orElseGet(Questionnaire::empty);
        sessions.save(session.generated(clock.now()), questionnaire);
        tickets.findBySession(sessionId).ifPresent(t -> tickets.save(p.origin() == VersionOrigin.CONFLICT_MERGE
                ? t.merged(p.jiraUpdated(), p.jiraDescriptionHash())
                : t.localAhead()));
        return version;
    }
}
