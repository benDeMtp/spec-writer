package fr.kawamind.infrastructure.llm;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.TokenStream;
import fr.kawamind.domain.conversation.NeedDraft;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.TurnTools;
import fr.kawamind.domain.sources.ModuleQualification;
import fr.kawamind.infrastructure.llm.ai.AgentAi;
import fr.kawamind.infrastructure.llm.ai.AgentTools;
import fr.kawamind.infrastructure.llm.ai.ConversationAi;
import fr.kawamind.infrastructure.llm.ai.SpecificationAi;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import io.quarkiverse.langchain4j.ChatMemoryRemover;
import org.jboss.logging.Logger;

/**
 * Port {@link ConversationLlm} sur les AI Services (014, research R3, R5) : l'agent est un AI Service
 * en flux dont le framework tient la boucle d'outils ; les opérations structurées passent par leurs
 * garde-fous de sortie. Cet adaptateur ne fait que construire les données balisées et convertir les
 * erreurs.
 */
public class LangChainConversationLlm implements ConversationLlm {

    private static final Logger LOG = Logger.getLogger(LangChainConversationLlm.class);

    /** Identifiant de la mémoire de chaque tour, retirée à la fin du tour. */
    private static final AtomicLong TURNS = new AtomicLong();

    private final AgentAi agent;
    private final ConversationAi conversation;
    private final SpecificationAi specification;
    private final Duration timeout;

    public LangChainConversationLlm(AgentAi agent, ConversationAi conversation, SpecificationAi specification,
            Duration timeout) {
        this.agent = agent;
        this.conversation = conversation;
        this.specification = specification;
        this.timeout = timeout;
    }

    @Override
    public void thinking(Consumer<String> reasoning, Runnable work) {
        ScopedValue.where(ReasoningSink.CURRENT, reasoning).run(work);
    }

    /**
     * Un tour de l'agent : réponse en flux, outils exécutés par le framework sur ce fil. L'arrêt
     * demandé coupe le flux en cours ({@link CallCancellation}) ; le texte reçu est rendu.
     */
    @Override
    public String converse(AgentRequest request, TurnTools tools, Consumer<String> deltas) {
        long start = System.nanoTime();
        StringBuilder text = new StringBuilder();
        CompletableFuture<Void> done = new CompletableFuture<>();
        Consumer<String> reasoning = ReasoningSink.current().orElse(null);
        String memory = "tour-" + TURNS.incrementAndGet();
        long[] firstDelta = {0};
        try {
            ScopedValue.where(CallTimeout.CURRENT, timeout).where(CallCancellation.CURRENT, tools::cancelled)
                    .where(AgentTools.CURRENT, tools).run(() -> {
                TokenStream stream = agent.converse(memory, UserMessages.converseAgent(request),
                        InvocationParameters.from(Map.of(AgentTools.TURN, tools)));
                stream.onPartialResponse(delta -> {
                    if (!tools.cancelled()) {
                        if (text.isEmpty()) {
                            firstDelta[0] = System.nanoTime();
                        }
                        text.append(delta);
                        deltas.accept(delta);
                    }
                })
                        .onPartialThinking(t -> {
                            if (reasoning != null && t.text() != null) {
                                reasoning.accept(t.text());
                            }
                        })
                        .onCompleteResponse(r -> done.complete(null))
                        .onError(done::completeExceptionally)
                        .start();
            });
            done.get();
            // SC-004 : délai avant le premier morceau visible de la réponse
            LOG.infof("LLM converse-agent : tour en %d ms, réponse visible à %d ms, %d caractères",
                    (System.nanoTime() - start) / 1_000_000,
                    firstDelta[0] == 0 ? -1 : (firstDelta[0] - start) / 1_000_000, text.length());
            return text.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
        } catch (ExecutionException e) {
            throw AiCalls.domain(e.getCause());
        } catch (RuntimeException e) {
            throw AiCalls.domain(e);
        } finally {
            ChatMemoryRemover.remove(agent, memory);
        }
    }

    @Override
    public List<ModuleQualification> qualify(QualifyRequest request) {
        return AiCalls.call(timeout, () -> conversation.qualify(UserMessages.qualifyPerimeter(request),
                LlmSchemas.params(LlmSchemas.PERIMETER_QUALIFICATION))).modules();
    }

    @Override
    public NeedDraft draftNeed(DraftRequest request) {
        return AiCalls.call(timeout, () -> conversation.draftNeed(UserMessages.needDraft(request),
                LlmSchemas.params(LlmSchemas.NEED_DRAFT)));
    }

    @Override
    public String compact(CompactRequest request) {
        return AiCalls.call(timeout, () -> conversation.compact(UserMessages.compact(request),
                LlmSchemas.params(LlmSchemas.COMPACT_MEMORY))).text();
    }

    @Override
    public Revision revise(ReviseRequest request) {
        return AiCalls.call(timeout, () -> specification.revise(UserMessages.revise(request),
                LlmSchemas.params(LlmSchemas.REVISION)));
    }

    @Override
    public List<ChangeCandidate> changes(ChangesRequest request) {
        return AiCalls.call(timeout, () -> specification.changes(UserMessages.changes(request),
                LlmSchemas.params(LlmSchemas.CHANGE_CANDIDATES))).changes();
    }
}
