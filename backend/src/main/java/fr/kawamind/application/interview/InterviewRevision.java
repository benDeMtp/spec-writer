package fr.kawamind.application.interview;

import fr.kawamind.application.conversation.DiscussSpecification;
import fr.kawamind.application.conversation.ProposeRevision;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.conversation.ConversationMessage;
import fr.kawamind.domain.conversation.MessageRole;
import fr.kawamind.domain.conversation.MessageState;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewOutcome;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConversationRepository;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Révision d'une spécification d'après la séance (021, US3, research.md R3, R4, R11, R12) : les
 * décisions entrent dans la discussion de la spécification comme un seul message, rédigé par le
 * code et marqué de la séance ; la rédaction est celle de la 011, et l'issue de la séance est
 * appliquée à la version proposée.
 */
public class InterviewRevision {

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** La rédaction lancée : la discussion où la suivre et son tour. */
    public record Started(UUID conversationId, UUID turnId) {
    }

    private final InterviewRepository interviews;
    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final ConversationRepository conversations;
    private final DiscussSpecification discussions;
    private final ProposeRevision propose;
    private final ConsentPolicy consent;
    private final ClockPort clock;
    private final IdGenerator ids;

    public InterviewRevision(InterviewRepository interviews, SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, DiscussSpecification discussions, ProposeRevision propose,
            ConsentPolicy consent, ClockPort clock, IdGenerator ids) {
        this.interviews = interviews;
        this.sessions = sessions;
        this.versions = versions;
        this.conversations = conversations;
        this.discussions = discussions;
        this.propose = propose;
        this.consent = consent;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * Raison pour laquelle la révision de cette spécification ne se propose pas, en clair ; vide si
     * elle se propose (FR-014).
     */
    public Optional<String> unavailable(Interview interview, UUID sessionId, List<InterviewPoint> points,
            Integer appliedVersion) {
        if (appliedVersion != null) {
            return Optional.of("Version " + appliedVersion + " appliquée.");
        }
        if (interview.state() != Interview.State.FINISHED) {
            return Optional.of("La révision se propose une fois la séance terminée.");
        }
        if (decided(points, sessionId).isEmpty()) {
            return Optional.of("Aucune décision à intégrer : rien n'a été tranché ni reporté sur cette spécification.");
        }
        if (sessions.implementation(sessionId).map(i -> i.status() == ImplementationStatus.IMPLEMENTED).orElse(false)) {
            return Optional.of("La spécification est implémentée : elle ne se modifie plus.");
        }
        if (!consent.isGranted(ConsentService.COPILOT)) {
            return Optional.of("La révision est rédigée par GitHub Copilot : donnez votre accord dans la "
                    + "Configuration, puis revenez ici.");
        }
        return Optional.empty();
    }

    /** « Proposer la nouvelle version » : le message de séance, puis la rédaction de la 011. */
    public synchronized Started propose(UUID interviewId, UUID sessionId) {
        Interview interview = interviews.find(interviewId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        var spec = interviews.specs(interviewId).stream().filter(s -> s.sessionId().equals(sessionId)).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (interview.state() != Interview.State.FINISHED) {
            throw new DomainException(ErrorCode.INTERVIEW_NOT_FINISHED);
        }
        List<InterviewPoint> points = interviews.points(interviewId);
        Optional<String> reason = unavailable(interview, sessionId, points, spec.appliedVersion());
        if (reason.isPresent()) {
            ErrorCode code = consent.isGranted(ConsentService.COPILOT) ? ErrorCode.VALIDATION_FAILED
                    : ErrorCode.CONSENT_REQUIRED;
            throw new DomainException(code, reason.get());
        }
        List<InterviewPoint> decided = decided(points, sessionId);
        DiscussSpecification.View discussion = discussions.open(sessionId);
        UUID cid = discussion.conversationId();
        if (!tagged(cid, interviewId)) {
            Language language = sessions.find(sessionId).map(s -> s.language()).orElse(Language.FR);
            SpecificationVersion read = versions.find(sessionId,
                    spec.version() == null ? discussion.version() : spec.version()).orElse(null);
            SpecificationVersion latest = versions.find(sessionId, discussion.version())
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            String text = message(interview, decided, read == null ? latest.content() : read.content(),
                    latest.content(), language);
            int ordinal = conversations.messages(cid).size() + 1;
            ConversationMessage m = new ConversationMessage(ids.next(), cid, ordinal, MessageRole.USER, text,
                    MessageState.COMPLETE, false, clock.now(), discussion.version());
            conversations.saveMessage(m);
            interviews.tagMessage(m.id(), interviewId);
            conversations.find(cid).ifPresent(c -> conversations.save(c.touched(clock.now())));
        }
        ProposeRevision.Started started = propose.propose(cid, List.of(), InterviewOutcome.Decisions.of(decided),
                interviewId);
        return new Started(cid, started.turnId());
    }

    private boolean tagged(UUID conversationId, UUID interviewId) {
        return conversations.messages(conversationId).stream()
                .anyMatch(m -> interviews.interviewOfMessage(m.id()).filter(interviewId::equals).isPresent());
    }

    private static List<InterviewPoint> decided(List<InterviewPoint> points, UUID sessionId) {
        return points.stream().filter(p -> p.sessionId().equals(sessionId) && p.outcome() != null).toList();
    }

    /**
     * Message de séance (contracts/llm-output.md) : une ligne par décision, puis les questions
     * reportées. Une ancre absente de la dernière version est omise (R11). Coupé à la taille d'une
     * réponse de l'agent, la plus grande qu'un message de discussion admet.
     */
    static String message(Interview interview, List<InterviewPoint> points, SpecificationContent read,
            SpecificationContent latest, Language language) {
        boolean en = language == Language.EN;
        Instant at = interview.startedAt() == null ? interview.createdAt() : interview.startedAt();
        var date = at.atZone(ZoneId.systemDefault()).toLocalDate();
        StringBuilder out = new StringBuilder(en ? "Session of " + date : "Séance du " + FR.format(date));
        if (!interview.participants().isEmpty()) {
            out.append(en ? " with " : " avec ").append(String.join(", ", interview.participants()));
        }
        // « Paul D. » finit déjà la phrase
        out.append(out.charAt(out.length() - 1) == '.' ? "" : ".")
                .append(en ? " Decisions to include:" : " Décisions à intégrer :");
        Map<String, String> statements = statements(read);
        List<InterviewPoint> deferred = new ArrayList<>();
        for (InterviewPoint p : points) {
            if (p.outcome() == InterviewPoint.Outcome.DEFERRED) {
                deferred.add(p);
                continue;
            }
            String detail = !p.text().isEmpty() ? p.text() : statements.getOrDefault(p.originCode(), "");
            out.append("\n- ").append(head(p, latest, language)).append(" — ").append(outcome(p.outcome(), en));
            if (!detail.isEmpty()) {
                out.append(en ? ": " : " : ").append(detail);
            }
        }
        if (!deferred.isEmpty()) {
            out.append(en ? "\nDeferred questions (added as open questions, do not rephrase them):"
                    : "\nQuestions reportées (ajoutées comme points ouverts, ne pas les reformuler) :");
            for (InterviewPoint p : deferred) {
                String head = head(p, latest, language);
                out.append("\n- ").append(head.isEmpty() ? "" : head + " — ").append(p.text());
                if (p.owner() != null) {
                    out.append(" (").append(p.owner()).append(')');
                }
            }
        }
        String text = out.toString();
        return text.length() > ConversationMessage.MAX_AGENT ? text.substring(0, ConversationMessage.MAX_AGENT) : text;
    }

    private static String head(InterviewPoint p, SpecificationContent latest, Language language) {
        Anchor anchor = Anchor.orNull(p.anchor(), latest);
        String label = anchor == null ? "" : anchor.label(language);
        if (p.originCode() == null) {
            return label;
        }
        return label.isEmpty() ? p.originCode() : p.originCode() + " (" + label + ")";
    }

    private static String outcome(InterviewPoint.Outcome outcome, boolean en) {
        return switch (outcome) {
            case CONFIRMED -> en ? "confirmed" : "confirmé";
            case CHANGE -> en ? "to change" : "à modifier";
            case REMOVE -> en ? "to remove" : "à retirer";
            case DEFERRED -> en ? "deferred" : "reporté";
        };
    }

    private static Map<String, String> statements(SpecificationContent content) {
        Map<String, String> statements = new HashMap<>();
        content.assumptions().forEach(a -> statements.put(a.code(), a.statement()));
        content.sourceDeviations().forEach(d -> statements.put(d.code(), d.difference()));
        content.openQuestions().forEach(q -> statements.put(q.code(), q.question()));
        return statements;
    }
}
