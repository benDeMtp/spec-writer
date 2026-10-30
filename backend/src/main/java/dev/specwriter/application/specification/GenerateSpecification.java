package dev.specwriter.application.specification;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.scenario.ScenarioStructureValidator;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.IdentifierAssigner;
import dev.specwriter.domain.specification.InputFingerprint;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.StructureViolations;
import dev.specwriter.domain.specification.TraceabilityValidator;
import dev.specwriter.domain.specification.VersionOrigin;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Génère une version de la spécification (FR-010 à FR-015, FR-018). À entrées identiques, la
 * version existante est renvoyée sans appel au LLM. Une sortie qui échoue au contrôle de structure
 * ou de traçabilité est redemandée 2 fois avec ses écarts, puis refusée : aucune version invalide
 * n'est enregistrée.
 */
public class GenerateSpecification {

    static final int MAX_ATTEMPTS = 3;

    public record Generated(SpecificationVersion version, boolean reused) {
    }

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final String toolVersion;

    public GenerateSpecification(SessionRepository sessions, SpecificationRepository versions, LlmClient llm,
            ConsentPolicy consent, ClockPort clock, IdGenerator ids, String toolVersion) {
        this.sessions = sessions;
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
        String fingerprint = InputFingerprint.of(session.need().text(), treated, toolVersion, session.language());
        var existing = versions.findByFingerprint(sessionId, fingerprint);
        if (existing.isPresent()) {
            return new Generated(existing.get(), true);
        }
        consent.requireGranted(ConsentService.COPILOT);

        Set<UUID> treatedIds = treated.stream().map(Question::id).collect(Collectors.toSet());
        GenerationRequest request = new GenerationRequest(session.need().text(), session.language(), answers(treated));
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            DraftSpecification draft = llm.generateSpecification(request);
            IdentifierAssigner.Assigned assigned = IdentifierAssigner.assign(draft, treated, session.language());
            StructureViolations violations = assigned.violations()
                    .and(ScenarioStructureValidator.validate(assigned.content()))
                    .and(TraceabilityValidator.validate(assigned.content(), treatedIds));
            if (violations.isEmpty()) {
                SpecificationVersion version = new SpecificationVersion(ids.next(), sessionId,
                        versions.lastNumber(sessionId) + 1, assigned.content(), fingerprint, VersionOrigin.GENERATED,
                        MarkdownRenderer.render(assigned.content(), session.language()), clock.now());
                versions.save(version);
                sessions.save(session.generated(clock.now()), questionnaire);
                return new Generated(version, false);
            }
            request = request.withCorrection(String.join("\n", violations.messages()));
        }
        throw new DomainException(ErrorCode.LLM_INVALID_OUTPUT);
    }

    private static List<QuestionAndAnswer> answers(List<Question> treated) {
        return treated.stream()
                .map(q -> new QuestionAndAnswer(q.id().toString(), q.theme(), q.label(), q.answer().value(),
                        q.answer().assumption()))
                .toList();
    }
}
