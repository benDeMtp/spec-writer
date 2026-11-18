package fr.kawamind.application.conversation;

import fr.kawamind.application.conversation.TurnEvents.Event;
import fr.kawamind.application.sources.AnalyzePerimeter;
import fr.kawamind.application.sources.PerimeterView;
import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.conversation.Citations;
import fr.kawamind.domain.conversation.Conversation;
import fr.kawamind.domain.conversation.ConversationMessage;
import fr.kawamind.domain.conversation.MessageRole;
import fr.kawamind.domain.conversation.MessageState;
import fr.kawamind.domain.conversation.SearchStep;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.conversation.Thinking;
import fr.kawamind.domain.conversation.TurnSource;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.KnowledgeBudget;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.ConversationLlm.Exchange;
import fr.kawamind.domain.port.ConversationRepository;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.ModelCapacities;
import fr.kawamind.domain.port.PerimeterRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.TurnTools;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.session.LanguageDetector;
import fr.kawamind.domain.sources.PerimeterAnalysis;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.jboss.logging.Logger;

/**
 * Un tour de parole avec l'agent (010 research.md R2, R4 ; 014 research R5) : l'agent appelle ses
 * outils (jusqu'à 5 recherches dans la connaissance du projet, une analyse de périmètre), puis
 * rédige en flux une réponse qui cite ses sources. Le tour
 * tourne en tâche de fond ; ses étapes, son texte et son état sont enregistrés au fil de l'eau et
 * publiés pour l'écran. Aucun texte n'est journalisé.
 */
public class Converse {

    private static final Logger LOG = Logger.getLogger(Converse.class);
    static final String NEW_NAME = "Nouvelle conversation";
    /** Le texte partiel est enregistré tous les N morceaux : une fermeture brutale perd peu. */
    static final int SAVE_EVERY = 40;

    /** Message de l'utilisateur enregistré, et tour (= message de l'agent) qui y répond. */
    public record Started(UUID userMessageId, UUID turnId) {
    }

    private final ProjectRepository projects;
    private final ConversationRepository conversations;
    private final ConversationLlm llm;
    private final ConversationSearch search;
    private final ModelCapacities capacities;
    private final ConsentPolicy consent;
    private final OperationRegistry operations;
    private final TurnEvents events;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final Executor executor;
    private final DesignatedDocuments designated;
    private final ConversationHistory history;
    /** Discussions des spécifications (011) ; nul : aucune conversation n'en est une. */
    private final DiscussSpecification discussions;
    /** Analyse de périmètre dans les sources du code (013) ; nulle : l'agent ne la propose pas. */
    private final AnalyzePerimeter perimeter;
    private final PerimeterRepository perimeters;

    public Converse(ProjectRepository projects, ConversationRepository conversations, ConversationLlm llm,
            ConversationSearch search, ModelCapacities capacities, ConsentPolicy consent, OperationRegistry operations,
            TurnEvents events, ClockPort clock, IdGenerator ids, Executor executor) {
        this(projects, conversations, llm, search, DesignatedDocuments.NONE, capacities, consent, operations, events,
                clock, ids, executor);
    }

    /** Avec les documents désignés par @ et joints à la conversation (US3). */
    public Converse(ProjectRepository projects, ConversationRepository conversations, ConversationLlm llm,
            ConversationSearch search, DesignatedDocuments designated, ModelCapacities capacities,
            ConsentPolicy consent, OperationRegistry operations, TurnEvents events, ClockPort clock, IdGenerator ids,
            Executor executor) {
        this(projects, conversations, llm, search, designated, capacities, consent, operations, events, clock, ids,
                executor, null);
    }

    /**
     * Avec les discussions des spécifications (011) : la spécification discutée passe en tête des
     * sources, et une spécification implémentée ne se discute pas.
     */
    public Converse(ProjectRepository projects, ConversationRepository conversations, ConversationLlm llm,
            ConversationSearch search, DesignatedDocuments designated, ModelCapacities capacities,
            ConsentPolicy consent, OperationRegistry operations, TurnEvents events, ClockPort clock, IdGenerator ids,
            Executor executor, DiscussSpecification discussions) {
        this(projects, conversations, llm, search, designated, capacities, consent, operations, events, clock, ids,
                executor, discussions, null, null);
    }

    /** Avec l'analyse de périmètre dans les sources du code (013). */
    public Converse(ProjectRepository projects, ConversationRepository conversations, ConversationLlm llm,
            ConversationSearch search, DesignatedDocuments designated, ModelCapacities capacities,
            ConsentPolicy consent, OperationRegistry operations, TurnEvents events, ClockPort clock, IdGenerator ids,
            Executor executor, DiscussSpecification discussions, AnalyzePerimeter perimeter,
            PerimeterRepository perimeters) {
        this.perimeter = perimeter;
        this.perimeters = perimeters;
        this.discussions = discussions;
        this.designated = designated;
        this.history = new ConversationHistory(conversations, llm, clock);
        this.projects = projects;
        this.conversations = conversations;
        this.llm = llm;
        this.search = search;
        this.capacities = capacities;
        this.consent = consent;
        this.operations = operations;
        this.events = events;
        this.clock = clock;
        this.ids = ids;
        this.executor = executor;
    }

    /** Nouvelle conversation dans le projet ; elle prend le nom de son premier message. */
    public Conversation create(UUID projectId) {
        projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        Conversation c = new Conversation(ids.next(), projectId, NEW_NAME, clock.now(), clock.now());
        conversations.save(c);
        return c;
    }

    public Started send(UUID conversationId, String text) {
        Conversation conversation = require(conversationId);
        List<ConversationMessage> messages = conversations.messages(conversationId);
        ConversationMessage question = ConversationMessage.user(ids.next(), conversationId, messages.size() + 1, text,
                clock.now());
        requireEditable(conversationId);
        consent.requireGranted(ConsentService.COPILOT);
        synchronized (this) {
            requireIdle(conversationId);
            conversations.saveMessage(question);
            ConversationMessage answer = ConversationMessage.agentStarted(ids.next(), conversationId,
                    messages.size() + 2, clock.now());
            conversations.saveMessage(answer);
            Conversation named = messages.isEmpty() && NEW_NAME.equals(conversation.name())
                    ? conversation.renamed(Conversation.defaultName(question.text())) : conversation;
            conversations.save(named.touched(clock.now()));
            launch(conversation.projectId(), question, answer);
            return new Started(question.id(), answer.id());
        }
    }

    /** Relance la réponse incomplète qui suit le message {@code userMessageId}. */
    public Started retry(UUID conversationId, UUID userMessageId) {
        Conversation conversation = require(conversationId);
        List<ConversationMessage> messages = conversations.messages(conversationId);
        int at = indexOf(messages, userMessageId);
        if (at < 0 || at + 1 >= messages.size() || messages.get(at).role() != MessageRole.USER
                || messages.get(at + 1).state() != MessageState.INCOMPLETE) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Seule une réponse incomplète peut être réessayée.");
        }
        requireEditable(conversationId);
        consent.requireGranted(ConsentService.COPILOT);
        synchronized (this) {
            requireIdle(conversationId);
            ConversationMessage answer = messages.get(at + 1).withText("").finished(MessageState.RUNNING, false);
            conversations.clearTurn(answer.id());
            if (perimeters != null) {
                perimeters.clear(answer.id());
            }
            conversations.saveMessage(answer);
            conversations.save(conversation.touched(clock.now()));
            launch(conversation.projectId(), messages.get(at), answer);
            return new Started(userMessageId, answer.id());
        }
    }

    /** Arrête la réponse : le texte déjà écrit est gardé, la réponse est marquée incomplète. */
    public void stop(UUID conversationId, UUID turnId) {
        require(conversationId);
        boolean ours = conversations.messages(conversationId).stream().anyMatch(m -> m.id().equals(turnId));
        if (!ours) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        events.cancel(turnId);
    }

    private void launch(UUID projectId, ConversationMessage question, ConversationMessage answer) {
        events.open(answer.id());
        Instant begin = clock.now();
        events.publish(answer.id(), new Event.Started(begin));
        OperationRegistry.Operation operation = operations.begin(question.conversationId());
        executor.execute(() -> {
            try {
                run(projectId, question, answer, begin);
            } finally {
                operation.close();
            }
        });
    }

    /** Ce que le tour a produit jusqu'ici, gardé même quand il échoue. */
    private static final class Progress {
        ConversationMessage answer;
        final StringBuilder text = new StringBuilder();
        Sources sources = new Sources(0);
        int steps;

        Progress(ConversationMessage answer) {
            this.answer = answer;
        }
    }

    private void run(UUID projectId, ConversationMessage question, ConversationMessage started, Instant begin) {
        UUID turn = started.id();
        Progress progress = new Progress(started);
        ThinkingLog thinking = new ThinkingLog(turn);
        try {
            llm.thinking(thinking::reasoning, () -> turn(projectId, question, progress, thinking));
            Citations.Resolved resolved = Citations.resolve(progress.text.toString(), progress.sources.all());
            MessageState state = events.cancelled(turn) ? MessageState.INCOMPLETE : MessageState.COMPLETE;
            ConversationMessage answer = progress.answer;
            conversations.saveMessage(answer.withText(resolved.text()).finished(state, answer.historyTruncated()));
            conversations.saveSources(turn, resolved.cited());
            long duration = saveThinking(turn, thinking, begin);
            events.publish(turn, new Event.Sources(resolved.cited()));
            events.publish(turn, new Event.Done(state, answer.historyTruncated(), duration));
            LOG.infof("Conversation : tour %s en %d ms, %d recherche(s), réflexion de %d caractères", state, duration,
                    progress.steps, thinking.length());
        } catch (DomainException e) {
            fail(progress, thinking, begin, e.code(), e.userMessage());
        } catch (RuntimeException e) {
            LOG.errorf(e, "Conversation : erreur inattendue (%s)", e.getClass().getSimpleName());
            fail(progress, thinking, begin, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
        }
    }

    private void turn(UUID projectId, ConversationMessage question, Progress progress, ThinkingLog thinking) {
        UUID turn = progress.answer.id();
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        KnowledgeBudget budget = capacities.budget();
        String sheet = project.context().markdown();
        Language language = LanguageDetector.detect(question.text());
        List<ConversationMessage> all = conversations.messages(question.conversationId());
        ConversationHistory.Prepared history = this.history.prepare(question.conversationId(), all,
                question.ordinal(), language, budget, phase -> {
                    if (phase == ConversationHistory.Phase.STARTED) {
                        thinking.stage(Thinking.Stage.MEMORY);
                    }
                    events.publish(turn, new Event.Memory(phase));
                });
        Optional<DiscussSpecification.Context> discussed = discussions == null ? Optional.empty()
                : discussions.of(question.conversationId());
        ConversationLlm.Discussed about = discussed.map(c -> new ConversationLlm.Discussed(c.specificationName(),
                c.version())).orElse(null);
        progress.answer = progress.answer.finished(MessageState.RUNNING, history.truncated())
                .about(discussed.map(DiscussSpecification.Context::version).orElse(null));

        Sources sources = progress.sources = new Sources(budget.conversationSources()
                + (discussed.isPresent() ? budget.discussedSpecification() : 0));
        discussed.ifPresent(c -> DiscussionSources.add(sources, c, budget.discussedSpecification()));
        sources.add(designated.read(projectId, question.conversationId(), question.text(), all.stream()
                .filter(m -> m.role() == MessageRole.USER && m.ordinal() < question.ordinal())
                .map(ConversationMessage::text).toList(), budget,
                discussed.map(DiscussSpecification.Context::attachments).orElse(List.of())));
        boolean perimeterAvailable = perimeter != null && perimeter.available(projectId);
        List<String> lastTerms = perimeters == null ? List.of()
                : perimeters.last(question.conversationId()).map(PerimeterAnalysis::terms).orElse(List.of());
        Tools tools = new Tools(projectId, question, progress, sources, discussed, language, history.exchanges(),
                thinking);
        thinking.stage(Thinking.Stage.PLAN);
        ConversationMessage saving = progress.answer;
        int[] chunks = {0};
        llm.converse(new ConversationLlm.AgentRequest(language, sheet, history.memory(), history.exchanges(),
                question.text(), sources.all(), sources.notCompared(), SearchStep.MAX_STEPS, about, perimeterAvailable,
                lastTerms), tools, delta -> {
                    if (events.cancelled(turn)) {
                        return;
                    }
                    if (chunks[0] == 0) {
                        thinking.stage(Thinking.Stage.ANSWER);
                        thinking.flush();
                    }
                    progress.text.append(delta);
                    events.publish(turn, new Event.Delta(delta));
                    if (++chunks[0] % SAVE_EVERY == 0) {
                        conversations.saveMessage(saving.withText(progress.text.toString()));
                    }
                });
    }

    /**
     * Outils du tour (014, research R5) : la recherche et l'analyse de périmètre demandées par l'agent.
     * Les sources sont numérotées en continu, chaque étape est enregistrée et publiée ; le plafond de
     * {@link SearchStep#MAX_STEPS} étapes, l'analyse unique et l'arrêt demandé sont tenus ici.
     */
    private final class Tools implements TurnTools {
        private final UUID projectId;
        private final ConversationMessage question;
        private final Progress progress;
        private final Sources sources;
        private final Optional<DiscussSpecification.Context> discussed;
        private final Language language;
        private final List<Exchange> exchanges;
        private final ThinkingLog thinking;
        private boolean perimeterDone;

        Tools(UUID projectId, ConversationMessage question, Progress progress, Sources sources,
                Optional<DiscussSpecification.Context> discussed, Language language, List<Exchange> exchanges,
                ThinkingLog thinking) {
            this.projectId = projectId;
            this.question = question;
            this.progress = progress;
            this.sources = sources;
            this.discussed = discussed;
            this.language = language;
            this.exchanges = exchanges;
            this.thinking = thinking;
        }

        @Override
        public synchronized Found search(String query, Set<SourceKind> kinds) {
            if (cancelled() || stepsLeft() <= 0) {
                return new Found(List.of(), List.of());
            }
            UUID turn = progress.answer.id();
            thinking.flush();
            // la spécification discutée est déjà là, entière : ses extraits de recherche sont écartés
            List<ConversationSearch.Found> found = search.run(projectId, query, kinds).stream()
                    .filter(f -> discussed.map(c -> !DiscussionSources.isDiscussed(f.ref(), c)).orElse(true))
                    .toList();
            int before = sources.notCompared().size();
            List<TurnSource> numbered = sources.add(found);
            SearchStep step = new SearchStep(++progress.steps, query, kinds, found.size());
            conversations.saveStep(turn, step);
            events.publish(turn, new Event.Step(step));
            thinking.stage(Thinking.Stage.PLAN);
            List<String> notCompared = sources.notCompared();
            return new Found(numbered, notCompared.subList(before, notCompared.size()));
        }

        @Override
        public synchronized PerimeterOutcome perimeter(List<String> terms) {
            if (cancelled() || perimeterDone || stepsLeft() <= 0 || perimeter == null) {
                return new PerimeterOutcome(false, List.of());
            }
            perimeterDone = true;
            thinking.flush();
            Optional<List<TurnSource>> modules = analyzePerimeter(projectId, question, terms, progress, sources,
                    language, exchanges);
            thinking.stage(Thinking.Stage.PLAN);
            return modules.map(m -> new PerimeterOutcome(false, m)).orElseGet(() -> new PerimeterOutcome(true,
                    List.of()));
        }

        @Override
        public synchronized int stepsLeft() {
            return SearchStep.MAX_STEPS - progress.steps;
        }

        @Override
        public synchronized boolean perimeterDone() {
            return perimeterDone;
        }

        @Override
        public boolean cancelled() {
            return events.cancelled(progress.answer.id());
        }
    }

    /**
     * Analyse de périmètre d'un tour (013) : recensement publié aussitôt, puis qualification par
     * l'agent ; les modules deviennent des sources citables, rendues numérotées. Vide si le projet
     * n'a pas de racine de sources.
     */
    private Optional<List<TurnSource>> analyzePerimeter(UUID projectId, ConversationMessage question, List<String> terms, Progress progress,
            Sources sources, Language language, List<Exchange> exchanges) {
        UUID turn = progress.answer.id();
        PerimeterAnalysis previous = perimeters.last(question.conversationId()).orElse(null);
        PerimeterAnalysis analysis;
        try {
            analysis = perimeter.analyze(projectId, terms, previous, progress.steps + 1, () -> events.cancelled(turn));
        } catch (DomainException e) {
            // aucun terme valide : l'agent répond sans analyse
            return Optional.of(List.of());
        }
        progress.steps++;
        perimeters.save(turn, analysis);
        events.publish(turn, new Event.Perimeter(PerimeterView.of(analysis)));
        if (analysis.unavailable() != null) {
            return Optional.empty();
        }
        if (!events.cancelled(turn)) {
            analysis = perimeter.qualify(analysis, language, exchanges, question.text()).analysis();
            perimeters.save(turn, analysis);
            events.publish(turn, new Event.Perimeter(PerimeterView.of(analysis)));
        }
        return Optional.of(sources.add(PerimeterSources.of(analysis)));
    }

    private void fail(Progress progress, ThinkingLog thinking, Instant begin, ErrorCode code, String userMessage) {
        LOG.warnf("Conversation : tour interrompu (%s) après %d recherche(s)", code, progress.steps);
        ConversationMessage answer = progress.answer;
        conversations.saveMessage(answer.withText(progress.text.toString()).finished(MessageState.INCOMPLETE,
                answer.historyTruncated()));
        saveThinking(answer.id(), thinking, begin);
        events.publish(answer.id(), new Event.Error(code.name(), userMessage));
    }

    private long saveThinking(UUID turn, ThinkingLog thinking, Instant begin) {
        long duration = Math.max(0, Duration.between(begin, clock.now()).toMillis());
        conversations.saveThinking(turn, new Thinking(thinking.parts(), duration));
        return duration;
    }

    /**
     * Réflexion du tour, étape par étape : chaque morceau est publié pour l'écran et gardé, jusqu'à
     * {@link Thinking#MAX_TEXT} caractères ; la suite n'est ni publiée ni gardée. Un tour est un seul
     * flux (014, research R5) : la réflexion d'un appel au modèle est retenue jusqu'à ce que l'on
     * sache à quoi elle sert, puis publiée sous l'étape recherche (un outil s'exécute) ou réponse
     * (le premier morceau du texte arrive).
     */
    private final class ThinkingLog {
        private final UUID turn;
        private final List<Thinking.Part> done = new ArrayList<>();
        private Thinking.Stage stage;
        private final StringBuilder current = new StringBuilder();
        private final StringBuilder pending = new StringBuilder();
        private int length;

        ThinkingLog(UUID turn) {
            this.turn = turn;
        }

        synchronized void stage(Thinking.Stage next) {
            close();
            stage = next;
            events.publish(turn, new Event.Stage(next));
        }

        synchronized void reasoning(String delta) {
            if (delta != null && !delta.isEmpty()) {
                pending.append(delta);
            }
        }

        /** Publie la réflexion retenue sous l'étape en cours. */
        synchronized void flush() {
            if (pending.isEmpty() || stage == null || length >= Thinking.MAX_TEXT) {
                pending.setLength(0);
                return;
            }
            String kept = pending.length() > Thinking.MAX_TEXT - length
                    ? pending.substring(0, Thinking.MAX_TEXT - length) : pending.toString();
            pending.setLength(0);
            current.append(kept);
            length += kept.length();
            events.publish(turn, new Event.Reasoning(kept));
        }

        synchronized List<Thinking.Part> parts() {
            flush();
            close();
            return List.copyOf(done);
        }

        synchronized int length() {
            return length;
        }

        /** Une étape sans réflexion n'est pas gardée. */
        private void close() {
            if (stage != null && !current.isEmpty()) {
                done.add(new Thinking.Part(stage, current.toString()));
            }
            current.setLength(0);
        }
    }

    /** Sources du tour, numérotées dans l'ordre de découverte, dans la part des sources. */
    static final class Sources {
        private final Map<String, TurnSource> byRef = new LinkedHashMap<>();
        private final List<String> notCompared = new ArrayList<>();
        private int left;

        Sources(int budget) {
            this.left = budget;
        }

        /** Section de la spécification réduite à son titre : nommée parmi les sources non comparées. */
        void notCompared(String title) {
            notCompared.add(title);
        }

        /** Ajoute les sources trouvées ; rend celles qui ont pris un numéro, dans l'ordre. */
        List<TurnSource> add(List<ConversationSearch.Found> found) {
            List<TurnSource> added = new ArrayList<>();
            for (ConversationSearch.Found f : found) {
                String key = f.kind() + "|" + f.ref();
                if (byRef.containsKey(key) || notCompared.contains(f.title())) {
                    continue;
                }
                int size = f.title().length() + f.text().length();
                if (size > left) {
                    notCompared.add(f.title());
                    continue;
                }
                left -= size;
                TurnSource source = new TurnSource(byRef.size() + 1, f.kind(), f.ref(), f.title(), f.text(),
                        f.provenance());
                byRef.put(key, source);
                added.add(source);
            }
            return added;
        }

        List<TurnSource> all() {
            return List.copyOf(byRef.values());
        }

        List<String> notCompared() {
            return List.copyOf(notCompared);
        }
    }

    private Conversation require(UUID conversationId) {
        return conversations.find(conversationId)
                .orElseThrow(() -> new DomainException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    private void requireEditable(UUID conversationId) {
        if (discussions != null) {
            discussions.requireEditable(conversationId);
        }
    }

    private void requireIdle(UUID conversationId) {
        if (operations.running(conversationId)) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                    "L'agent répond encore : attendez la fin de sa réponse, ou arrêtez-la.");
        }
    }

    private static int indexOf(List<ConversationMessage> messages, UUID id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
