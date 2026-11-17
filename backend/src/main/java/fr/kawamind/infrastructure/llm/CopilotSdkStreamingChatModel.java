package fr.kawamind.infrastructure.llm;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import fr.kawamind.infrastructure.llm.CopilotSdkChatModel.Prompt;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.Reply;
import java.util.List;
import java.util.Set;

/**
 * {@link StreamingChatModel} LangChain4j au-dessus du Copilot SDK (014, contracts/llm-adapter.md) :
 * mêmes messages et mêmes outils terminaux que {@link CopilotSdkChatModel}, réponse transmise au fil
 * de l'eau. L'arrêt ({@code StreamingHandle.cancel()} ou {@link CallCancellation}) abandonne la
 * requête ; la session est supprimée comme pour un appel d'un bloc.
 */
public class CopilotSdkStreamingChatModel implements StreamingChatModel {

    private final CopilotSdkGateway gateway;
    private final ModelSelection modelSelection;
    private final List<ChatModelListener> listeners;

    public CopilotSdkStreamingChatModel(CopilotSdkGateway gateway, ModelSelection modelSelection) {
        this(gateway, modelSelection, List.of());
    }

    public CopilotSdkStreamingChatModel(CopilotSdkGateway gateway, ModelSelection modelSelection, List<ChatModelListener> listeners) {
        this.gateway = gateway;
        this.modelSelection = modelSelection;
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
        Prompt prompt = Prompt.of(request);
        String model = request.modelName() != null ? request.modelName() : modelSelection.currentModel().orElse(null);
        CancellableHandle handle = new CancellableHandle();
        PartialResponseContext context = new PartialResponseContext(handle);
        Reply reply;
        try {
            reply = gateway.call(new CopilotSdkGateway.Call(model, prompt.system(), prompt.user(),
                    CopilotSdkChatModel.schema(request), CopilotSdkChatModel.tools(request),
                    delta -> handler.onPartialResponse(new PartialResponse(delta), context),
                    thinking -> handler.onPartialThinking(new PartialThinking(thinking)),
                    () -> handle.isCancelled() || CallCancellation.requested()));
        } catch (CopilotSdkGateway.GatewayException e) {
            handler.onError(CopilotSdkChatModel.toDomain(e));
            return;
        } catch (RuntimeException e) {
            handler.onError(e);
            return;
        }
        handler.onCompleteResponse(CopilotSdkChatModel.response(reply, model));
    }
}
