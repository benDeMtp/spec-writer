package fr.kawamind.application.conversation;

import fr.kawamind.application.conversation.TurnEvents.Event;
import fr.kawamind.application.knowledge.SessionKnowledge;
import fr.kawamind.application.specification.DraftValidation;
import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.application.specification.ProposalStore;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.conversation.CitedSource;
import fr.kawamind.domain.conversation.ConversationMessage;
import fr.kawamind.domain.conversation.MessageRole;
import fr.kawamind.domain.conversation.MessageState;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.conversation.Thinking;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewOutcome;
import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.llm.QuestionAndAnswer;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.ConversationLlm.DiscussionLine;
import fr.kawamind.domain.port.ConversationLlm.Revision;
import fr.kawamind.domain.port.ConversationRepository;
import fr.kawamind.domain.port.PerimeterRepository;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.ModelCapacities;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.question.Question;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.session.SpecificationLock;
import fr.kawamind.domain.sources.ModuleCorrection;
import fr.kawamind.domain.sources.PerimeterSnapshot;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.UseCase;
import fr.kawamind.domain.specification.VersionOrigin;
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
    /** Séances (021) : titre d'un message de séance cité comme source ; nul sans séance. */
    private final InterviewRepository interviews;

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
        this(sessions, versions, conversations, discussions, knowledge, llm, capacities, consent, operations, events,
                proposals, clock, ids, executor, perimeters, null);
    }

    public ProposeRevision(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, DiscussSpecification discussions, SessionKnowledge knowledge,
            ConversationLlm llm, ModelCapacities capacities, ConsentPolicy consent, OperationRegistry operations,
            TurnEvents events, ProposalStore proposals, ClockPort clock, IdGenerator ids, Executor executor,
            PerimeterRepository perimeters, InterviewRepository interviews) {
        this.interviews = interviews;
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
        return propose(conversationId, confirmed, null, null);
    }

    /**
     * Rédaction qui intègre les décisions de la séance {@code interviewId} (021, research.md R3, R4) :
     * {@code outcome} est appliqué à la version rédigée avant qu'elle soit proposée.
     */
    public Started propose(UUID conversationId, List<String> confirmed, InterviewOutcome.Decisions outcome,
            UUID interviewId) {
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
            events.open(turn, TurnEvents.Kind.REVISION, conversationId);
            Instant begin = clock.now();
            events.publish(turn, new Event.Started(begin));
            // la discussion ne reçoit pas de message, et la spécification ne se supprime pas, pendant la rédaction
            OperationRegistry.Operation onConversation = operations.begin(conversationId);
            OperationRegistry.Operation onSpecification = operations.begin(context.sessionId());
            executor.execute(() -> {
                try {
                    run(context, turn, begin, outcome, interviewId);
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

    private void run(DiscussSpecification.Context context, UUID turn, Instant begin, InterviewOutcome.Decisions outcome,
            UUID interviewId) {
        AtomicInteger reasoning = new AtomicInteger();
        try {
            List<ProposalStore.Proposal> proposed = new ArrayList<>(1);
            llm.thinking(delta -> reason(turn, delta, reasoning), () -> proposed.add(revise(context, turn, outcome, interviewId)));
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
            // pile complète : le journal en expurge les messages (JournalFormatter)
            LOG.errorf(e, "Discussion : erreur inattendue pendant la proposition (%s, %s)",
                    e.getClass().getSimpleName(), origin(e));
            events.publish(turn, new Event.Error(ErrorCode.INTERNAL_ERROR.name(),
                    ErrorCode.INTERNAL_ERROR.defaultMessage()));
        }
    }

    private ProposalStore.Proposal revise(DiscussSpecification.Context context, UUID turn,
            InterviewOutcome.Decisions outcome, UUID interviewId) {
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
        List<DiscussionLine> lines = lines(cid, messages, prepared, citable, m -> sourceTitle(m, language));
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
            // seul le périmètre change, ou seule l'issue de la séance : la version reprend le contenu courant
            if (outcome == null && (analysed == null || analysed.corrected(u.corrections).equals(current.orElse(null)))) {
                throw u;
            }
            corrections.addAll(u.corrections);
            content = outcome == null ? base.content() : InterviewOutcome.unchanged(base.content());
            perimeterOnly = u;
        }
        content = withBase(content, base.content(), outcome != null);
        if (outcome != null) {
            // les écarts sont repris de la base, dans l'ordre : chacun garde son code d'origine
            content = InterviewOutcome.apply(content.withSourceDeviations(content.sourceDeviations().stream()
                    .map(d -> d.fromCode() == null ? d.withFromCode(d.code()) : d).toList()), base.content(), outcome);
        } else {
            content = withoutFromCodes(content);
        }
        PerimeterSnapshot perimeter = analysed == null ? null : analysed.corrected(corrections);
        ProposalStore.Proposal p = proposals.propose(session, base, content, VersionOrigin.DISCUSSION, null, null,
                known, cid, perimeter == null || perimeter.equals(current.orElse(null)) ? null : perimeter, interviewId);
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
            ConversationHistory.Prepared prepared, Map<String, String> citable,
            java.util.function.Function<ConversationMessage, String> titles) {
        List<ConversationMessage> said = messages.stream().filter(m -> !m.text().isBlank()).toList();
        int offset = said.size() - prepared.exchanges().size();
        List<DiscussionLine> lines = new ArrayList<>();
        for (int i = 0; i < prepared.exchanges().size(); i++) {
            ConversationLlm.Exchange e = prepared.exchanges().get(i);
            ConversationMessage m = offset + i >= 0 ? said.get(offset + i) : null;
            String ref = null;
            if (e.role() == MessageRole.USER && m != null && m.role() == MessageRole.USER) {
                ref = "discussion:" + cid + "#" + m.ordinal();
                citable.put(ref, titles.apply(m));
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

    /**
     * Titre de la source : celui de la séance pour un message de séance (021, FR-017), sinon celui
     * du message de discussion.
     */
    private String sourceTitle(ConversationMessage m, Language language) {
        Optional<Interview> interview = interviews == null ? Optional.empty()
                : interviews.interviewOfMessage(m.id()).flatMap(interviews::find);
        return interview.map(i -> title(i, language)).orElseGet(() -> title(m, language));
    }

    /** « Séance du 20/11/2026 (Julie M., Paul D.) » : la séance citée comme source (021, R4). */
    static String title(Interview interview, Language language) {
        Instant at = interview.startedAt() == null ? interview.createdAt() : interview.startedAt();
        var date = at.atZone(ZoneId.systemDefault()).toLocalDate();
        String head = language == Language.EN ? "Session of " + date : "Séance du " + FR.format(date);
        return interview.participants().isEmpty() ? head
                : head + " (" + String.join(", ", interview.participants()) + ")";
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

    /**
     * Le format de la révision n'a pas d'écarts entre sources (016), ni points ouverts ni ancres (021,
     * {@code LlmSchemas.REVISION}) : la version proposée reprend les écarts et les points ouverts de la
     * version discutée, et chaque hypothèse reprise ({@code fromCode}) garde l'ancre de la base si son
     * élément existe encore. Pour une séance, les points ouverts repris portent leur propre code, que
     * l'issue de la séance retire s'il est tranché.
     */
    static SpecificationContent withBase(SpecificationContent revised, SpecificationContent base, boolean interview) {
        SpecificationContent out = withBaseDeviations(revised, base);
        if (out.openQuestions().isEmpty() && !base.openQuestions().isEmpty()) {
            out = out.withOpenQuestions(base.openQuestions().stream()
                    .map(q -> interview ? q.withFromCode(q.code()) : q).toList());
        }
        java.util.Map<String, fr.kawamind.domain.specification.Anchor> anchors = new java.util.HashMap<>();
        base.assumptions().stream().filter(a -> a.anchor() != null).forEach(a -> anchors.put(a.code(), a.anchor()));
        SpecificationContent target = out;
        return out.withAssumptions(out.assumptions().stream()
                .map(a -> a.anchor() == null && a.fromCode() != null && anchors.containsKey(a.fromCode())
                        ? a.withAnchor(fr.kawamind.domain.specification.Anchor.orNull(anchors.get(a.fromCode()), target))
                        : a)
                .toList());
    }

    static SpecificationContent withBaseDeviations(SpecificationContent revised, SpecificationContent base) {
        return revised.sourceDeviations().isEmpty() && !base.sourceDeviations().isEmpty()
                ? revised.withSourceDeviations(base.sourceDeviations()) : revised;
    }

    /** Hors séance, les codes d'origine ne valent que pour la rédaction : ils ne sont pas gardés. */
    static SpecificationContent withoutFromCodes(SpecificationContent c) {
        return c.withAssumptions(c.assumptions().stream().map(a -> a.withFromCode(null)).toList())
                .withOpenQuestions(c.openQuestions().stream().map(q -> q.withFromCode(null)).toList())
                .withSourceDeviations(c.sourceDeviations().stream().map(d -> d.withFromCode(null)).toList());
    }

    /** Premier appel du code de l'outil dans la pile d'une erreur, pour la situer sans son message. */
    static String origin(Throwable e) {
        for (StackTraceElement f : e.getStackTrace()) {
            if (f.getClassName().startsWith("fr.kawamind.")) {
                return f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName()
                        + ":" + f.getLineNumber();
            }
        }
        return "hors de l'outil";
    }
}
