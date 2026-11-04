package dev.specwriter.application.specification;

import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.EditRequest;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import java.util.List;
import java.util.UUID;

/**
 * Modification demandée à l'IA en langage naturel (FR-044, US6-4). Renvoie une proposition et son
 * diff, sans créer de version : rien n'est appliqué avant {@link AcceptAiEdit}.
 */
public class ProposeAiEdit {

    public static final int MAX_INSTRUCTION = 2000;

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ProposalStore proposals;
    private final SessionKnowledge knowledge;

    public ProposeAiEdit(SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LlmClient llm, ConsentPolicy consent, ProposalStore proposals) {
        this(sessions, versions, llm, consent, proposals, SessionKnowledge.sheetOnly(projects));
    }

    public ProposeAiEdit(SessionRepository sessions, SpecificationRepository versions, LlmClient llm,
            ConsentPolicy consent, ProposalStore proposals, SessionKnowledge knowledge) {
        this.sessions = sessions;
        this.versions = versions;
        this.llm = llm;
        this.consent = consent;
        this.proposals = proposals;
        this.knowledge = knowledge;
    }

    public ProposalStore.Proposal run(UUID sessionId, String instruction) {
        String text = instruction == null ? "" : instruction.strip();
        if (text.isEmpty() || text.length() > MAX_INSTRUCTION) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "L'instruction doit faire entre 1 et 2 000 caractères.");
        }
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationVersion base = latest(versions, sessionId);
        consent.requireGranted(ConsentService.COPILOT);
        List<Question> treated = sessions.questionnaire(sessionId).map(Questionnaire::treated).orElse(List.of());
        List<QuestionAndAnswer> answers = DraftValidation.answers(treated);
        ProjectKnowledge knowledge = this.knowledge.forSpecification(session, answers);
        EditRequest request = new EditRequest(session.need().text(), session.language(), answers, base.markdown(), text,
                null, knowledge);
        SpecificationContent content = DraftValidation.produce(c -> llm.editSpecification(request.withCorrection(c)),
                treated, session.language(), ErrorCode.LLM_INVALID_OUTPUT, knowledge);
        return proposals.propose(session, base, content, VersionOrigin.AI_EDIT, null, null, knowledge);
    }

    static SpecificationVersion latest(SpecificationRepository versions, UUID sessionId) {
        int last = versions.lastNumber(sessionId);
        return versions.find(sessionId, last).orElseThrow(() ->
                new DomainException(ErrorCode.VALIDATION_FAILED, "Générez d'abord une spécification."));
    }
}
