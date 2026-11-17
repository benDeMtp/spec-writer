package fr.kawamind.application.specification;

import fr.kawamind.application.knowledge.SessionKnowledge;
import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.llm.QuestionAndAnswer;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.llm.EditRequest;
import fr.kawamind.domain.port.LlmClient;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.question.Question;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.session.SpecificationLock;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.VersionOrigin;
import java.util.List;
import java.util.UUID;

/**
 * Modification demandée à l'IA en langage naturel (FR-044, US6-4). Renvoie une proposition et son
 * diff, sans créer de version : rien n'est appliqué avant {@link AcceptAiEdit}.
 */
public class ProposeAiEdit {

    public static final int MAX_INSTRUCTION = 2000;

    private final OperationRegistry operations;
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
        this(sessions, versions, llm, consent, proposals, knowledge, new OperationRegistry());
    }

    /** {@code operations} : la proposition est une opération de l'IA, qui bloque les suppressions (FR-019 de 002). */
    public ProposeAiEdit(SessionRepository sessions, SpecificationRepository versions, LlmClient llm,
            ConsentPolicy consent, ProposalStore proposals, SessionKnowledge knowledge, OperationRegistry operations) {
        this.operations = operations;
        this.sessions = sessions;
        this.versions = versions;
        this.llm = llm;
        this.consent = consent;
        this.proposals = proposals;
        this.knowledge = knowledge;
    }

    public ProposalStore.Proposal run(UUID sessionId, String instruction) {
        OperationRegistry.Operation operation = operations.begin(sessionId);
        try {
            return propose(sessionId, instruction);
        } finally {
            operation.close();
        }
    }

    private ProposalStore.Proposal propose(UUID sessionId, String instruction) {
        String text = instruction == null ? "" : instruction.strip();
        if (text.isEmpty() || text.length() > MAX_INSTRUCTION) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "L'instruction doit faire entre 1 et 2 000 caractères.");
        }
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationLock.requireEditable(sessions, sessionId);
        SpecificationVersion base = latest(versions, sessionId);
        consent.requireGranted(ConsentService.COPILOT);
        List<Question> treated = sessions.questionnaire(sessionId).map(Questionnaire::treated).orElse(List.of());
        List<QuestionAndAnswer> answers = DraftValidation.answers(treated);
        ProjectKnowledge knowledge = this.knowledge.forEdit(session, answers, text, base.markdown());
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
