package fr.kawamind.infrastructure.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import fr.kawamind.infrastructure.llm.CopilotSdkChatModel.Prompt;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejeu en flux (E2E, dev, tests) : mêmes entrées que {@link ReplayChatModel}. Un texte est rendu par
 * groupes de quelques mots ; des appels d'outils sont rendus d'un bloc ; la réflexion est rejouée
 * avant la réponse.
 */
public final class ReplayStreamingChatModel implements StreamingChatModel {

    private static final Pattern CHUNK = Pattern.compile("(?:\\S+\\s*){1,4}|\\s+");

    private final ReplayChatModel replay;
    private final List<ChatModelListener> listeners;

    public ReplayStreamingChatModel(ReplayChatModel replay) {
        this(replay, List.of());
    }

    public ReplayStreamingChatModel(ReplayChatModel replay, List<ChatModelListener> listeners) {
        this.replay = replay;
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        ChatResponse response;
        ReplayChatModel.Entry entry;
        Prompt prompt = Prompt.of(request);
        try {
            entry = replay.find(prompt.system(), prompt.user());
            response = ReplayChatModel.response(entry, prompt.user());
        } catch (RuntimeException e) {
            handler.onError(e);
            return;
        }
        if (entry.reasoning() != null) {
            handler.onPartialThinking(new PartialThinking(entry.reasoning()));
        }
        if (response.aiMessage().hasToolExecutionRequests()) {
            handler.onCompleteResponse(response);
            return;
        }
        CancellableHandle handle = new CancellableHandle();
        PartialResponseContext context = new PartialResponseContext(handle);
        StringBuilder sent = new StringBuilder();
        Matcher m = CHUNK.matcher(response.aiMessage().text());
        while (m.find() && !handle.isCancelled() && !CallCancellation.requested()) {
            sent.append(m.group());
            handler.onPartialResponse(new PartialResponse(m.group()), context);
        }
        handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(sent.toString()))
                .finishReason(FinishReason.STOP).build());
    }
}
