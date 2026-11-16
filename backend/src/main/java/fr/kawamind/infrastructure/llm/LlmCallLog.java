package fr.kawamind.infrastructure.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import fr.kawamind.domain.error.DomainException;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Journal des appels au modèle (014, research R4) : opération (lue dans le marqueur de la consigne),
 * durée, numéro de tentative et outils demandés. Jamais de texte de consigne, de donnée ni de
 * réponse (FR-008).
 */
public final class LlmCallLog implements ChatModelListener {

    private static final Logger LOG = Logger.getLogger(LlmCallLog.class);
    private static final String START = "specwriter.start";

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(START, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        AiMessage ai = context.chatResponse().aiMessage();
        List<String> tools = ai != null && ai.hasToolExecutionRequests()
                ? ai.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList()
                : List.of();
        LOG.infof("LLM %s : %d ms, %d tentative(s)%s", operation(context.chatRequest()), elapsed(context.attributes()),
                attempt(context.chatRequest()), tools.isEmpty() ? "" : ", outils : " + String.join(", ", tools));
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        Throwable error = context.error();
        String code = error instanceof DomainException d ? d.code().name() : error.getClass().getSimpleName();
        if (Thread.currentThread().isInterrupted()) {
            LOG.infof("LLM %s : appel interrompu à la tentative %d", operation(context.chatRequest()),
                    attempt(context.chatRequest()));
            return;
        }
        // la cause est une erreur métier dont la cause, sans contenu (SafeThrowable), donne la phase et la pile
        LOG.warnf(error instanceof DomainException ? error : null, "LLM %s : échec à la tentative %d après %d ms (%s)",
                operation(context.chatRequest()), attempt(context.chatRequest()), elapsed(context.attributes()), code);
    }

    static String operation(ChatRequest request) {
        return request.messages().stream().filter(SystemMessage.class::isInstance).map(SystemMessage.class::cast)
                .findFirst().flatMap(s -> Operations.of(s.text())).orElse("inconnue");
    }

    /** Une reprise demandée par un garde-fou ajoute un message utilisateur après le premier. */
    static int attempt(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        long users = messages.stream().filter(UserMessage.class::isInstance).count();
        return (int) Math.max(1, users);
    }

    private static long elapsed(java.util.Map<Object, Object> attributes) {
        Object start = attributes.get(START);
        return start instanceof Long l ? (System.nanoTime() - l) / 1_000_000 : 0;
    }
}
