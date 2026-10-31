package dev.specwriter.infrastructure.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link ChatModel} LangChain4j au-dessus du Copilot SDK (research.md R3). Les messages système et
 * utilisateur restent séparés (FR-026, LLM01). Le SDK n'expose pas la température : le déterminisme
 * repose sur l'empreinte des entrées (research.md R12). Rien n'est journalisé (FR-025).
 */
public class CopilotSdkChatModel implements ChatModel {

    private final CopilotSdkGateway gateway;
    private final ModelSelection modelSelection;

    public CopilotSdkChatModel(CopilotSdkGateway gateway, ModelSelection modelSelection) {
        this.gateway = gateway;
        this.modelSelection = modelSelection;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        List<String> system = new ArrayList<>();
        List<String> user = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            if (m instanceof SystemMessage s) {
                system.add(s.text());
            } else if (m instanceof UserMessage u) {
                user.add(u.singleText());
            } else {
                throw new IllegalArgumentException("Type de message non pris en charge : " + m.type());
            }
        }
        Map<String, Object> schema = schema(request.parameters() == null ? null : request.parameters().responseFormat());
        String model = request.modelName() != null ? request.modelName() : modelSelection.currentModel().orElse(null);
        try {
            String text = gateway.complete(model, String.join("\n\n", system), String.join("\n\n", user), schema);
            return ChatResponse.builder().aiMessage(AiMessage.from(text)).modelName(model).build();
        } catch (CopilotSdkGateway.GatewayException e) {
            throw switch (e.kind()) {
                case NOT_CONNECTED -> new DomainException(ErrorCode.COPILOT_NOT_CONNECTED);
                case MODEL_UNAVAILABLE -> new DomainException(ErrorCode.MODEL_UNAVAILABLE);
                case QUOTA_EXHAUSTED -> new DomainException(ErrorCode.QUOTA_EXHAUSTED);
                case CLI_OUTDATED -> new DomainException(ErrorCode.COPILOT_CLI_OUTDATED);
                case UNAVAILABLE -> new DomainException(ErrorCode.LLM_UNAVAILABLE);
            };
        }
    }

    private static Map<String, Object> schema(ResponseFormat format) {
        if (format == null || format.type() != ResponseFormatType.JSON || format.jsonSchema() == null) {
            return null;
        }
        return StrictJsonSchema.strict(JsonSchemaElementJsonUtils.toMap(format.jsonSchema().rootElement()));
    }
}
