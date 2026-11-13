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
import dev.specwriter.domain.port.PerimeterRepository;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SpecificationLock;
import dev.specwriter.domain.sources.ModuleCorrection;
import dev.specwriter.domain.sources.PerimeterSnapshot;
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
import java.util.Optional;
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

    /** {@code message} : le message des changements retenus, ajouté à la discussion ; nul sans changement coché. */
    public record Started(UUID turnId, ConversationMessage message) {

        public Started(UUID turnId) {
            this(turnId, null);
        }
    }

    /** Au plus autant de changements retenus, chacun d'au plus {@value #MAX_CHANGE} caractères. */
    public static final int MAX_CHANGES = 20;
    public static final int MAX_CHANGE = 1_000;

    /** La discussion n'a établi aucun changement : l'agent l'a dit, sans version. */
    private static final class Unchanged extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String reason;
        private final transient List<ModuleCorrection> corrections;

        Unchanged(String reason, List<ModuleCorrection> corrections) {
            super(null, null, false, false);
            this.reason = reason;
            this.corrections = corrections;
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
    /** Analyses de périmètre de la discussion (013, T056) ; nul : les modules ne se corrigent pas. */
    private final PerimeterRepository perimeters;

    public ProposeRevision(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, DiscussSpecification discussions, SessionKnowledge knowledge,
            ConversationLlm llm, ModelCapacities capacities, ConsentPolicy consent, OperationRegistry operations,
            TurnEvents events, ProposalStore proposals, ClockPort clock, IdGenerator ids, Executor executor) {
        this(sessions, versions, conversations, discussions, knowledge, llm, capacities, consent, operations, events,
                proposals, clock, ids, executor, null);
    }

    public ProposeRevision(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, DiscussSpecification discussions, SessionKnowledge knowledge,
            ConversationLlm llm, ModelCapacities capacities, ConsentPolicy consent, OperationRegistry operations,
            TurnEvents events, ProposalStore proposals, ClockPort clock, IdGenerator ids, Executor executor,
            PerimeterRepository perimeters) {
        this.perimeters = perimeters;
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
        return propose(conversationId, List.of());
    }

    /**
     * Rédige la nouvelle version ; les changements retenus par la personne entrent d'abord dans la
     * discussion, comme un message de sa part : ils sont alors établis, et tracés dans le fil.
     */
    public Started propose(UUID conversationId, List<String> confirmed) {
        List<String> retained = retained(confirmed);
        DiscussSpecification.Context context = require(conversationId);
        consent.requireGranted(ConsentService.COPILOT);
        synchronized (this) {
            requireIdle(conversationId);
            ConversationMessage message = null;
            if (!retained.isEmpty()) {
                List<ConversationMessage> messages = conversations.messages(conversationId);
                message = ConversationMessage.user(ids.next(), conversationId, messages.size() + 1,
                        confirmation(retained, sessions.find(context.sessionId()).map(Session::language)
                                .orElse(Language.FR)), clock.now()).about(context.version());
                conversations.saveMessage(message);
                conversations.find(conversationId).ifPresent(c -> conversations.save(c.touched(clock.now())));
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
            return new Started(turn, message);
        }
    }

    /**
     * Changements de la spécification que la discussion avance, à cocher avant la rédaction ; liste
     * vide si elle n'en avance aucun. Rien n'est gardé.
     */
    public List<ConversationLlm.ChangeCandidate> changes(UUID conversationId) {
        DiscussSpecification.Context context = require(conversationId);
        consent.requireGranted(ConsentService.COPILOT);
        requireIdle(conversationId);
        Session session = sessions.find(context.sessionId()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationVersion base = versions.find(session.id(), context.version())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        List<ConversationMessage> messages = conversations.messages(conversationId);
        ConversationHistory.Prepared prepared = history.prepare(conversationId, messages, Integer.MAX_VALUE,
                session.language(), capacities.budget(), phase -> { });
        List<DiscussionLine> lines = prepared.exchanges().stream()
                .map(e -> new DiscussionLine(e.role(), e.text(), null)).toList();
        long begin = System.nanoTime();
        List<ConversationLlm.ChangeCandidate> found = llm.changes(new ConversationLlm.ChangesRequest(session.language(),
                base.markdown(), prepared.memory(), lines));
        LOG.infof("Discussion : %d changements relevés en %d ms", found.size(), (System.nanoTime() - begin) / 1_000_000);
        return found;
    }

    private DiscussSpecification.Context require(UUID conversationId) {
        conversations.find(conversationId).orElseThrow(() -> new DomainException(ErrorCode.CONVERSATION_NOT_FOUND));
        DiscussSpecification.Context context = discussions.of(conversationId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_A_DISCUSSION));
        SpecificationLock.requireEditable(sessions, context.sessionId());
        if (conversations.messages(conversationId).stream().noneMatch(m -> m.role() == MessageRole.USER)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Discutez d'abord de la spécification avec l'agent : la discussion est vide.");
        }
        return context;
    }

    private void requireIdle(UUID conversationId) {
        if (operations.running(conversationId)) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                    "L'agent travaille encore : attendez la fin de sa réponse, ou arrêtez-la.");
        }
    }

    /** Changements non vides, sans doublon, bornés en nombre et en longueur. */
    static List<String> retained(List<String> confirmed) {
        List<String> kept = new ArrayList<>();
        for (String c : confirmed == null ? List.<String>of() : confirmed) {
            String text = c == null ? "" : c.strip().replaceAll("\\s+", " ");
            if (text.isEmpty() || kept.contains(text)) {
                continue;
            }
            if (text.length() > MAX_CHANGE) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED,
                        "Un changement dépasse " + MAX_CHANGE + " caractères : raccourcissez-le.");
            }
            kept.add(text);
        }
        if (kept.size() > MAX_CHANGES) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Retenez au plus " + MAX_CHANGES + " changements à la fois.");
        }
        return List.copyOf(kept);
    }

    /** Message des changements retenus, ajouté à la discussion au nom de la personne. */
    static String confirmation(List<String> retained, Language language) {
        String head = language == Language.EN ? "Changes to include in the new version:"
                : "Changements retenus pour la nouvelle version :";
        return retained.stream().map(c -> "- " + c).collect(Collectors.joining("\n", head + "\n", ""));
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
        Optional<PerimeterSnapshot> current = proposals.perimeter(session.id());
        PerimeterSnapshot analysed = perimeters == null ? null
                : perimeters.last(cid).map(PerimeterSnapshot::of).orElse(current.orElse(null));
        ConversationLlm.ReviseRequest request = new ConversationLlm.ReviseRequest(language, session.need().text(),
                answers, base.markdown(), known, prepared.memory(), lines, cited, null, analysed);
        DraftValidation.Discussion discussion = new DraftValidation.Discussion(citable, base.content().useCases());
        List<ModuleCorrection> corrections = new ArrayList<>();
        SpecificationContent content;
        Unchanged perimeterOnly = null;
        try {
            content = DraftValidation.produce(correction -> {
                Revision revision = llm.revise(request.withCorrection(correction));
                if (!revision.changed()) {
                    throw new Unchanged(revision.reason(), revision.perimeter());
                }
                corrections.clear();
                corrections.addAll(revision.perimeter());
                return revision.draft();
            }, treated, language, ErrorCode.LLM_INVALID_OUTPUT, known, discussion);
        } catch (Unchanged u) {
            // seul le périmètre change : la version reprend le contenu courant avec les modules corrigés
            if (analysed == null || analysed.corrected(u.corrections).equals(current.orElse(null))) {
                throw u;
            }
            corrections.addAll(u.corrections);
            content = base.content();
            perimeterOnly = u;
        }
        PerimeterSnapshot perimeter = analysed == null ? null : analysed.corrected(corrections);
        ProposalStore.Proposal p = proposals.propose(session, base, content, VersionOrigin.DISCUSSION, null, null,
                known, cid, perimeter == null || perimeter.equals(current.orElse(null)) ? null : perimeter);
        if (perimeterOnly != null && p.markdown().equals(base.markdown())) {
            // le périmètre rendu est celui de la version courante : rien ne change vraiment
            proposals.discard(session.id(), p.id());
            throw perimeterOnly;
        }
        return p;
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
