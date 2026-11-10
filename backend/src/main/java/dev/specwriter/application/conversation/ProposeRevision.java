package dev.specwriter.application.conversation;

import dev.specwriter.application.conversation.TurnEvents.Event;
import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.application.specification.DraftValidation;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.conversation.Thinking;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.ConversationLlm.DiscussionLine;
import dev.specwriter.domain.port.ConversationLlm.Revision;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SpecificationLock;
import dev.specwriter.domain.specification.SourceRef;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.UseCase;
import dev.specwriter.domain.specification.VersionOrigin;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * « Proposer une nouvelle version » (011, US2, research.md R4, R5) : l'agent réécrit la dernière
 * version d'après la discussion, sans aucune saisie. La rédaction est un tour du flux de la
 * conversation (début, étape, réflexion, fin) ; la réflexion n'est pas gardée et rien n'est ajouté
 * au fil. La proposition attend sa relecture dans {@link ProposalStore} ; rien n'est créé avant
 * « Appliquer ».
 */
public class ProposeRevision {

    private static final Logger LOG = Logger.getLogger(ProposeRevision.class);
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Started(UUID turnId) {
    }

    /** La discussion n'a établi aucun changement : l'agent l'a dit, sans version. */
    private static final class Unchanged extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String reason;

        Unchanged(String reason) {
            super(null, null, false, false);
            this.reason = reason;
        }
    }

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final ConversationRepository conversations;
    private final DiscussSpecification discussions;
    private final SessionKnowledge knowledge;
    private final ConversationLlm llm;
    private final ModelCapacities capacities;
    private final ConsentPolicy consent;
    private final OperationRegistry operations;
    private final TurnEvents events;
    private final ProposalStore proposals;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final Executor executor;
    private final ConversationHistory history;

    public ProposeRevision(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, DiscussSpecification discussions, SessionKnowledge knowledge,
            ConversationLlm llm, ModelCapacities capacities, ConsentPolicy consent, OperationRegistry operations,
            TurnEvents events, ProposalStore proposals, ClockPort clock, IdGenerator ids, Executor executor) {
        this.sessions = sessions;
        this.versions = versions;
        this.conversations = conversations;
        this.discussions = discussions;
        this.knowledge = knowledge;
        this.llm = llm;
        this.capacities = capacities;
        this.consent = consent;
        this.operations = operations;
        this.events = events;
        this.proposals = proposals;
        this.clock = clock;
        this.ids = ids;
        this.executor = executor;
        this.history = new ConversationHistory(conversations, llm, clock);
    }

    public Started propose(UUID conversationId) {
        conversations.find(conversationId).orElseThrow(() -> new DomainException(ErrorCode.CONVERSATION_NOT_FOUND));
        DiscussSpecification.Context context = discussions.of(conversationId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_A_DISCUSSION));
        SpecificationLock.requireEditable(sessions, context.sessionId());
        if (conversations.messages(conversationId).stream().noneMatch(m -> m.role() == MessageRole.USER)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Discutez d'abord de la spécification avec l'agent : la discussion est vide.");
        }
        consent.requireGranted(ConsentService.COPILOT);
        synchronized (this) {
            if (operations.running(conversationId)) {
                throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                        "L'agent travaille encore : attendez la fin de sa réponse, ou arrêtez-la.");
            }
            UUID turn = ids.next();
            events.open(turn);
            Instant begin = clock.now();
            events.publish(turn, new Event.Started(begin));
            // la discussion ne reçoit pas de message, et la spécification ne se supprime pas, pendant la rédaction
            OperationRegistry.Operation onConversation = operations.begin(conversationId);
            OperationRegistry.Operation onSpecification = operations.begin(context.sessionId());
            executor.execute(() -> {
                try {
                    run(context, turn, begin);
                } finally {
                    onSpecification.close();
                    onConversation.close();
                }
            });
            return new Started(turn);
        }
    }

    private void run(DiscussSpecification.Context context, UUID turn, Instant begin) {
        AtomicInteger reasoning = new AtomicInteger();
        try {
            List<ProposalStore.Proposal> proposed = new ArrayList<>(1);
            llm.thinking(delta -> reason(turn, delta, reasoning), () -> proposed.add(revise(context, turn)));
            ProposalStore.Proposal p = proposed.getFirst();
            long duration = duration(begin);
            events.publish(turn, new Event.Done(MessageState.COMPLETE, false, duration, p.id(), false, null));
            LOG.infof("Discussion : version proposée en %d ms (%d use cases)", duration, p.content().useCases().size());
        } catch (Unchanged u) {
            long duration = duration(begin);
            events.publish(turn, new Event.Done(MessageState.COMPLETE, false, duration, null, true, u.reason));
            LOG.infof("Discussion : aucun changement établi (%d ms)", duration);
        } catch (DomainException e) {
            LOG.warnf("Discussion : proposition en échec (%s)", e.code());
            events.publish(turn, new Event.Error(e.code().name(), e.userMessage()));
        } catch (RuntimeException e) {
            LOG.errorf("Discussion : erreur inattendue pendant la proposition (%s)", e.getClass().getSimpleName());
            events.publish(turn, new Event.Error(ErrorCode.INTERNAL_ERROR.name(),
                    ErrorCode.INTERNAL_ERROR.defaultMessage()));
        }
    }

    private ProposalStore.Proposal revise(DiscussSpecification.Context context, UUID turn) {
        UUID cid = context.conversationId();
        Session session = sessions.find(context.sessionId()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationVersion base = versions.find(session.id(), context.version())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Language language = session.language();
        List<Question> treated = sessions.questionnaire(session.id()).map(Questionnaire::treated).orElse(List.of());
        List<QuestionAndAnswer> answers = DraftValidation.answers(treated);
        List<ConversationMessage> messages = conversations.messages(cid);
        ConversationHistory.Prepared prepared = history.prepare(cid, messages, Integer.MAX_VALUE, language,
                capacities.budget(), phase -> {
                    if (phase == ConversationHistory.Phase.STARTED) {
                        events.publish(turn, new Event.Stage(Thinking.Stage.MEMORY));
                    }
                    events.publish(turn, new Event.Memory(phase));
                });
        events.publish(turn, new Event.Stage(Thinking.Stage.REVISE));

        Map<String, String> citable = new LinkedHashMap<>();
        List<DiscussionLine> lines = lines(cid, messages, prepared, language, citable);
        List<CitedSource> cited = cited(messages, base, citable);
        String said = lines.stream().filter(l -> l.role() == MessageRole.USER).map(DiscussionLine::text)
                .collect(Collectors.joining("\n"));
        ProjectKnowledge known = knowledge.forEdit(session, answers, said, base.markdown());
        ConversationLlm.ReviseRequest request = new ConversationLlm.ReviseRequest(language, session.need().text(),
                answers, base.markdown(), known, prepared.memory(), lines, cited, null);
        DraftValidation.Discussion discussion = new DraftValidation.Discussion(citable, base.content().useCases());
        SpecificationContent content = DraftValidation.produce(correction -> {
            Revision revision = llm.revise(request.withCorrection(correction));
            if (!revision.changed()) {
                throw new Unchanged(revision.reason());
            }
            return revision.draft();
        }, treated, language, ErrorCode.LLM_INVALID_OUTPUT, known, discussion);
        return proposals.propose(session, base, content, VersionOrigin.DISCUSSION, null, null, known, cid);
    }

    /**
     * Échanges transmis ; les messages de l'utilisateur portent leur référence citable. La fenêtre
     * de l'historique est la fin des messages non vides.
     */
    private static List<DiscussionLine> lines(UUID cid, List<ConversationMessage> messages,
            ConversationHistory.Prepared prepared, Language language, Map<String, String> citable) {
        List<ConversationMessage> said = messages.stream().filter(m -> !m.text().isBlank()).toList();
        int offset = said.size() - prepared.exchanges().size();
        List<DiscussionLine> lines = new ArrayList<>();
        for (int i = 0; i < prepared.exchanges().size(); i++) {
            ConversationLlm.Exchange e = prepared.exchanges().get(i);
            ConversationMessage m = offset + i >= 0 ? said.get(offset + i) : null;
            String ref = null;
            if (e.role() == MessageRole.USER && m != null && m.role() == MessageRole.USER) {
                ref = "discussion:" + cid + "#" + m.ordinal();
                citable.put(ref, title(m, language));
            }
            lines.add(new DiscussionLine(e.role(), e.text(), ref));
        }
        return lines;
    }

    /**
     * Sources citées par les réponses de la discussion, puis messages de discussions passées que la
     * version courante cite : un use case gardé garde sa source.
     */
    private List<CitedSource> cited(List<ConversationMessage> messages, SpecificationVersion base,
            Map<String, String> citable) {
        Map<String, CitedSource> cited = new LinkedHashMap<>();
        messages.stream().filter(m -> m.role() == MessageRole.AGENT)
                .flatMap(m -> conversations.sources(m.id()).stream())
                .forEach(c -> cited.putIfAbsent(c.ref(), new CitedSource(cited.size() + 1, c.kind(), c.ref(), c.title())));
        for (UseCase uc : base.content().useCases()) {
            SourceRef s = uc.source();
            if (s != null && s.kind() == SourceRef.Kind.DISCUSSION && s.ref() != null && !citable.containsKey(s.ref())) {
                String title = s.excerpt() == null || s.excerpt().isBlank() ? s.ref() : s.excerpt();
                citable.put(s.ref(), title);
                cited.putIfAbsent(s.ref(), new CitedSource(cited.size() + 1, SourceKind.DISCUSSION, s.ref(), title));
            }
        }
        return List.copyOf(cited.values());
    }

    /** « Discussion du 11/11/2026, message 3 » : ce que la spécification écrit de la source. */
    static String title(ConversationMessage m, Language language) {
        var date = m.createdAt().atZone(ZoneId.systemDefault()).toLocalDate();
        return language == Language.EN
                ? "Discussion of " + date + ", message " + m.ordinal()
                : "Discussion du " + FR.format(date) + ", message " + m.ordinal();
    }

    /** Morceau de la réflexion, montré pendant la rédaction ; jamais gardé. */
    private void reason(UUID turn, String delta, AtomicInteger length) {
        if (delta == null || delta.isEmpty() || length.get() >= Thinking.MAX_TEXT) {
            return;
        }
        int room = Thinking.MAX_TEXT - length.get();
        String kept = delta.length() > room ? delta.substring(0, room) : delta;
        length.addAndGet(kept.length());
        events.publish(turn, new Event.Reasoning(kept));
    }

    private long duration(Instant begin) {
        return Math.max(0, Duration.between(begin, clock.now()).toMillis());
    }
}
