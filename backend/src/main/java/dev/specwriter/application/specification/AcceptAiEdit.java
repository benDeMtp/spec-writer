package dev.specwriter.application.specification;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SpecificationLock;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import java.util.UUID;

/**
 * Applique une proposition de l'IA (US6-5) : nouvelle version {@code AI_EDIT} ou
 * {@code CONFLICT_MERGE}. Jira n'est pas modifié (FR-046) ; un ticket lié passe à {@code LOCAL_AHEAD}.
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
        Questionnaire questionnaire = sessions.questionnaire(sessionId).orElseGet(Questionnaire::empty);
        sessions.save(session.generated(clock.now()), questionnaire);
        tickets.findBySession(sessionId).ifPresent(t -> tickets.save(p.origin() == VersionOrigin.CONFLICT_MERGE
                ? t.merged(p.jiraUpdated(), p.jiraDescriptionHash())
                : t.localAhead()));
        return version;
    }
}
