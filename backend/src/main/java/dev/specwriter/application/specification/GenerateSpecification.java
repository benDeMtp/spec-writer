package dev.specwriter.application.specification;

import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SpecificationLock;
import dev.specwriter.domain.specification.InputFingerprint;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import java.util.List;
import java.util.UUID;

/**
 * Génère une version de la spécification (FR-010 à FR-015, FR-018). À entrées identiques, la
 * version existante est renvoyée sans appel au LLM. Une sortie qui échoue au contrôle de structure
 * ou de traçabilité est redemandée 2 fois avec ses écarts, puis refusée : aucune version invalide
 * n'est enregistrée.
 */
public class GenerateSpecification {

    public record Generated(SpecificationVersion version, boolean reused) {
    }

    private final OperationRegistry operations;
    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final String toolVersion;
    private final SessionKnowledge knowledge;

    public GenerateSpecification(SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LlmClient llm, ConsentPolicy consent, ClockPort clock, IdGenerator ids, String toolVersion) {
        this(sessions, versions, llm, consent, clock, ids, toolVersion, SessionKnowledge.sheetOnly(projects));
    }

    public GenerateSpecification(SessionRepository sessions, SpecificationRepository versions, LlmClient llm,
            ConsentPolicy consent, ClockPort clock, IdGenerator ids, String toolVersion, SessionKnowledge knowledge) {
        this(sessions, versions, llm, consent, clock, ids, toolVersion, knowledge, new OperationRegistry());
    }

    /** {@code operations} : la rédaction est une opération de l'IA, qui bloque les suppressions (FR-019 de 002). */
    public GenerateSpecification(SessionRepository sessions, SpecificationRepository versions, LlmClient llm,
            ConsentPolicy consent, ClockPort clock, IdGenerator ids, String toolVersion, SessionKnowledge knowledge,
            OperationRegistry operations) {
        this.operations = operations;
        this.sessions = sessions;
        this.knowledge = knowledge;
        this.versions = versions;
        this.llm = llm;
        this.consent = consent;
        this.clock = clock;
        this.ids = ids;
        this.toolVersion = toolVersion;
    }

    public Generated run(UUID sessionId) {
        OperationRegistry.Operation operation = operations.begin(sessionId);
        try {
            return generate(sessionId);
        } finally {
            operation.close();
        }
    }

    private Generated generate(UUID sessionId) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationLock.requireEditable(sessions, sessionId);
        Questionnaire questionnaire = sessions.questionnaire(sessionId).orElseThrow();
        if (questionnaire.hasPendingContradiction()) {
            throw new DomainException(ErrorCode.CONTRADICTION_UNRESOLVED);
        }
        List<Question> treated = questionnaire.treated();
        List<QuestionAndAnswer> answers = DraftValidation.answers(treated);
        ProjectKnowledge used = knowledge.forSpecification(session, answers);
        String fingerprint = InputFingerprint.of(session.need().text(), treated, toolVersion, session.language(),
                used.fingerprint());
        var existing = versions.findByFingerprint(sessionId, fingerprint);
        if (existing.isPresent()) {
            return new Generated(existing.get(), true);
        }
        consent.requireGranted(ConsentService.COPILOT);

        GenerationRequest request = new GenerationRequest(session.need().text(), session.language(), answers, null, used);
        SpecificationContent content = DraftValidation.produce(
                correction -> llm.generateSpecification(correction == null ? request : request.withCorrection(correction)),
                treated, session.language(), ErrorCode.LLM_INVALID_OUTPUT, used);
        SpecificationVersion version = new SpecificationVersion(ids.next(), sessionId, versions.nextNumber(sessionId),
                content, fingerprint, VersionOrigin.GENERATED,
                MarkdownRenderer.render(content, session.language(), used.selection()), clock.now()).withKnowledge(used);
        // la feature a pu passer à « implémentée » pendant l'appel au LLM (research.md R7)
        SpecificationLock.requireEditable(sessions, sessionId);
        versions.save(version);
        sessions.save(session.generated(clock.now()), questionnaire);
        return new Generated(version, false);
    }
}
