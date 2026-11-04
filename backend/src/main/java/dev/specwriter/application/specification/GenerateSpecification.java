package dev.specwriter.application.specification;

import dev.specwriter.domain.llm.ProjectContextInput;
import dev.specwriter.domain.project.ContextSheets;
import dev.specwriter.application.project.ProjectContexts;
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

    private final SessionRepository sessions;
    private final ProjectRepository projects;
    private final SpecificationRepository versions;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final String toolVersion;

    public GenerateSpecification(SessionRepository sessions, ProjectRepository projects, SpecificationRepository versions,
            LlmClient llm, ConsentPolicy consent, ClockPort clock, IdGenerator ids, String toolVersion) {
        this.sessions = sessions;
        this.projects = projects;
        this.versions = versions;
        this.llm = llm;
        this.consent = consent;
        this.clock = clock;
        this.ids = ids;
        this.toolVersion = toolVersion;
    }

    public Generated run(UUID sessionId) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Questionnaire questionnaire = sessions.questionnaire(sessionId).orElseThrow();
        if (questionnaire.hasPendingContradiction()) {
            throw new DomainException(ErrorCode.CONTRADICTION_UNRESOLVED);
        }
        List<Question> treated = questionnaire.treated();
        ContextSheets context = ProjectContexts.of(projects, session);
        String fingerprint = InputFingerprint.of(session.need().text(), treated, toolVersion, session.language(),
                context.combinedHash());
        var existing = versions.findByFingerprint(sessionId, fingerprint);
        if (existing.isPresent()) {
            return new Generated(existing.get(), true);
        }
        consent.requireGranted(ConsentService.COPILOT);

        GenerationRequest request = new GenerationRequest(session.need().text(), session.language(),
                DraftValidation.answers(treated), null, ProjectContextInput.of(context));
        SpecificationContent content = DraftValidation.produce(
                correction -> llm.generateSpecification(correction == null ? request : request.withCorrection(correction)),
                treated, session.language(), ErrorCode.LLM_INVALID_OUTPUT, ProjectContextInput.of(context));
        SpecificationVersion version = new SpecificationVersion(ids.next(), sessionId, versions.lastNumber(sessionId) + 1,
                content, fingerprint, VersionOrigin.GENERATED, MarkdownRenderer.render(content, session.language()),
                clock.now()).withContext(context.combinedHash(), context.updatedAt());
        versions.save(version);
        sessions.save(session.generated(clock.now()), questionnaire);
        return new Generated(version, false);
    }
}
