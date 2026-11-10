package dev.specwriter.application.conversation;

import dev.specwriter.application.conversation.TurnEvents.Event;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.Citations;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.TurnSource;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.ConversationLlm.Exchange;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.LanguageDetector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.jboss.logging.Logger;

/**
 * Un tour de parole avec l'agent (010, research.md R1, R2, R4) : jusqu'à 5 recherches choisies par
 * l'IA dans la connaissance du projet, puis une réponse rédigée en flux qui cite ses sources. Le tour
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
        consent.requireGranted(ConsentService.COPILOT);
        synchronized (this) {
            requireIdle(conversationId);
            ConversationMessage answer = messages.get(at + 1).withText("").finished(MessageState.RUNNING, false);
            conversations.clearTurn(answer.id());
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
        OperationRegistry.Operation operation = operations.begin(question.conversationId());
        executor.execute(() -> {
            try {
                run(projectId, question, answer);
            } finally {
                operation.close();
            }
        });
    }

    private void run(UUID projectId, ConversationMessage question, ConversationMessage started) {
        UUID turn = started.id();
        long begin = System.nanoTime();
        StringBuilder text = new StringBuilder();
        ConversationMessage answer = started;
        int steps = 0;
        try {
            Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
            KnowledgeBudget budget = capacities.budget();
            String sheet = project.context().markdown();
            Language language = LanguageDetector.detect(question.text());
            List<ConversationMessage> all = conversations.messages(question.conversationId());
            ConversationHistory.Prepared history = this.history.prepare(question.conversationId(), all,
                    question.ordinal(), language, budget, phase -> events.publish(turn, new Event.Memory(phase)));
            answer = answer.finished(MessageState.RUNNING, history.truncated());

            Sources sources = new Sources(budget.conversationSources());
            sources.add(designated.read(projectId, question.conversationId(), question.text(), all.stream()
                    .filter(m -> m.role() == MessageRole.USER && m.ordinal() < question.ordinal())
                    .map(ConversationMessage::text).toList(), budget));
            boolean stopped = false;
            while (!events.cancelled(turn)) {
                if (steps == SearchStep.MAX_STEPS) {
                    stopped = true;
                    break;
                }
                ConversationLlm.Plan plan = llm.plan(new ConversationLlm.PlanRequest(language, sheet, history.memory(),
                        history.exchanges(), question.text(), sources.all(), SearchStep.MAX_STEPS - steps));
                if (plan.action() == ConversationLlm.Action.ANSWER || plan.query() == null || plan.query().isBlank()) {
                    break;
                }
                List<ConversationSearch.Found> found = search.run(projectId, plan.query(), plan.kinds());
                sources.add(found);
                SearchStep step = new SearchStep(++steps, plan.query(), plan.kinds(), found.size());
                conversations.saveStep(turn, step);
                events.publish(turn, new Event.Step(step));
            }
            if (!events.cancelled(turn)) {
                ConversationMessage saving = answer;
                int[] chunks = {0};
                llm.answer(new ConversationLlm.AnswerRequest(language, sheet, history.memory(), history.exchanges(),
                        question.text(), sources.all(), sources.notCompared(), stopped), delta -> {
                            text.append(delta);
                            events.publish(turn, new Event.Delta(delta));
                            if (++chunks[0] % SAVE_EVERY == 0) {
                                conversations.saveMessage(saving.withText(text.toString()));
                            }
                        }, () -> events.cancelled(turn));
            }
            Citations.Resolved resolved = Citations.resolve(text.toString(), sources.all());
            MessageState state = events.cancelled(turn) ? MessageState.INCOMPLETE : MessageState.COMPLETE;
            conversations.saveMessage(answer.withText(resolved.text()).finished(state, answer.historyTruncated()));
            conversations.saveSources(turn, resolved.cited());
            events.publish(turn, new Event.Sources(resolved.cited()));
            events.publish(turn, new Event.Done(state, answer.historyTruncated()));
            LOG.infof("Conversation : tour %s en %d ms, %d recherche(s)", state, (System.nanoTime() - begin) / 1_000_000,
                    steps);
        } catch (DomainException e) {
            fail(answer, text, e.code(), e.userMessage(), steps);
        } catch (RuntimeException e) {
            LOG.errorf("Conversation : erreur inattendue (%s)", e.getClass().getSimpleName());
            fail(answer, text, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), steps);
        }
    }

    private void fail(ConversationMessage answer, StringBuilder text, ErrorCode code, String userMessage, int steps) {
        LOG.warnf("Conversation : tour interrompu (%s) après %d recherche(s)", code, steps);
        conversations.saveMessage(answer.withText(text.toString()).finished(MessageState.INCOMPLETE,
                answer.historyTruncated()));
        events.publish(answer.id(), new Event.Error(code.name(), userMessage));
    }

    /** Sources du tour, numérotées dans l'ordre de découverte, dans la part des sources. */
    static final class Sources {
        private final Map<String, TurnSource> byRef = new LinkedHashMap<>();
        private final List<String> notCompared = new ArrayList<>();
        private int left;

        Sources(int budget) {
            this.left = budget;
        }

        void add(List<ConversationSearch.Found> found) {
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
                byRef.put(key, new TurnSource(byRef.size() + 1, f.kind(), f.ref(), f.title(), f.text()));
            }
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
