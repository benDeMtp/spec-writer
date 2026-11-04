package dev.specwriter.application.specification;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SpecificationLock;
import java.util.UUID;

/**
 * Suppression définitive d'une version locale (FR-014, FR-015, FR-017 à FR-019 de 002, research.md
 * R8). Jira n'est jamais appelé ; le lien suit la dernière version restante. Sans version, la
 * session redevient prête à générer, avec son besoin et ses réponses.
 */
public class DeleteVersion {

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final ProposalStore proposals;
    private final OperationRegistry operations;
    private final ClockPort clock;

    public DeleteVersion(SessionRepository sessions, SpecificationRepository versions, LinkedTicketRepository tickets,
            ProposalStore proposals, OperationRegistry operations, ClockPort clock) {
        this.sessions = sessions;
        this.versions = versions;
        this.tickets = tickets;
        this.proposals = proposals;
        this.operations = operations;
        this.clock = clock;
    }

    public void run(UUID sessionId, int number) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationLock.requireEditable(sessions, sessionId);
        if (operations.running(sessionId)) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                    "L'IA travaille sur cette spécification. Attendez la fin de l'opération, puis supprimez la version.");
        }
        versions.find(sessionId, number).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        versions.delete(sessionId, number);
        proposals.discard(sessionId);
        int latest = versions.lastNumber(sessionId);
        tickets.findBySession(sessionId).ifPresent(t -> tickets.save(t.afterVersionDeleted(number, latest)));
        if (latest == 0) {
            Questionnaire questionnaire = sessions.questionnaire(sessionId).orElse(Questionnaire.empty());
            sessions.save(session.readyToGenerate(clock.now()), questionnaire);
        }
    }
}
