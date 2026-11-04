package dev.specwriter.application.context;

import dev.specwriter.application.context.ContextProposalStore.ContextProposal;
import dev.specwriter.application.context.ContextProposalStore.ProposalQuestion;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ContextQuestionRepository;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.project.ContextQuestion;
import java.util.UUID;

/**
 * Répond à une question de clarification d'une proposition, ou la passe (FR-013h). La réponse est
 * enregistrée aussitôt avec le projet sous un numéro {@code Q-n}, pour ne pas la perdre si la
 * proposition expire (FR-013i, research.md R16).
 */
public class AnswerContextQuestion {

    private final ContextProposalStore proposals;
    private final ContextQuestionRepository questions;
    private final IdGenerator ids;
    private final ClockPort clock;

    public AnswerContextQuestion(ContextProposalStore proposals, ContextQuestionRepository questions, IdGenerator ids,
            ClockPort clock) {
        this.proposals = proposals;
        this.questions = questions;
        this.ids = ids;
        this.clock = clock;
    }

    public ContextQuestion run(UUID projectId, UUID proposalId, UUID questionId, String answer, boolean skip) {
        ContextProposal proposal = proposals.find(projectId, proposalId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette proposition n'existe plus. Régénérez-la."));
        ProposalQuestion asked = proposal.question(questionId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette question n'existe plus."));
        ContextQuestion recorded = asked.number() == null
                ? ContextQuestion.recorded(ids.next(), projectId, questions.nextNumber(projectId), asked.sheet(),
                        asked.label(), asked.rationale(), answer, skip, asked.askedAt(), clock.now())
                : questions.find(projectId, asked.number()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND))
                        .reanswered(answer, skip, clock.now());
        questions.save(recorded);
        proposals.updateQuestion(proposal, asked.recorded(recorded));
        return recorded;
    }
}
