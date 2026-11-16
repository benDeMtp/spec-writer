package fr.kawamind.infrastructure.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.CapturedCall;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.Reply;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ChatModel} LangChain4j au-dessus du Copilot SDK (014, contracts/llm-adapter.md). La consigne
 * reste seule en message système ; les données, puis l'historique des appels d'outils, vont dans le
 * message utilisateur, balisés et échappés. Les outils de la requête deviennent des outils terminaux
 * du SDK : leur appel est capturé et rendu au framework, qui l'exécute (research R2). Rien n'est
 * journalisé.
 */
public class CopilotSdkChatModel implements ChatModel {

    private final CopilotSdkGateway gateway;
    private final ModelSelection modelSelection;
    private final List<ChatModelListener> listeners;

    public CopilotSdkChatModel(CopilotSdkGateway gateway, ModelSelection modelSelection) {
        this(gateway, modelSelection, List.of());
    }

    public CopilotSdkChatModel(CopilotSdkGateway gateway, ModelSelection modelSelection, List<ChatModelListener> listeners) {
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
    public ChatResponse doChat(ChatRequest request) {
        Prompt prompt = Prompt.of(request);
        String model = model(request);
        try {
            Reply reply = gateway.call(new CopilotSdkGateway.Call(model, prompt.system(), prompt.user(),
                    schema(request), tools(request), null, ReasoningSink.current().orElse(null), null));
            return response(reply, model);
        } catch (CopilotSdkGateway.GatewayException e) {
            throw toDomain(e);
        }
    }

    String model(ChatRequest request) {
        return request.modelName() != null ? request.modelName() : modelSelection.currentModel().orElse(null);
    }

    /** Réponse LangChain4j : les appels d'outils capturés, sinon le texte. */
    static ChatResponse response(Reply reply, String model) {
        if (!reply.toolCalls().isEmpty()) {
            List<ToolExecutionRequest> calls = reply.toolCalls().stream().map(CopilotSdkChatModel::request).toList();
            return ChatResponse.builder().aiMessage(AiMessage.from(calls)).finishReason(FinishReason.TOOL_EXECUTION)
                    .modelName(model).build();
        }
        return ChatResponse.builder().aiMessage(AiMessage.from(reply.text())).finishReason(FinishReason.STOP)
                .modelName(model).build();
    }

    private static ToolExecutionRequest request(CapturedCall call) {
        return ToolExecutionRequest.builder().id(call.id()).name(call.name()).arguments(call.arguments()).build();
    }

    /** Erreur métier d'une erreur du SDK ; la cause, sans contenu (SafeThrowable), donne au journal la phase et la pile. */
    static DomainException toDomain(CopilotSdkGateway.GatewayException e) {
        ErrorCode code = switch (e.kind()) {
            case NOT_CONNECTED -> ErrorCode.COPILOT_NOT_CONNECTED;
            case MODEL_UNAVAILABLE -> ErrorCode.MODEL_UNAVAILABLE;
            case QUOTA_EXHAUSTED -> ErrorCode.QUOTA_EXHAUSTED;
            case CLI_OUTDATED -> ErrorCode.COPILOT_CLI_OUTDATED;
            case UNAVAILABLE -> ErrorCode.LLM_UNAVAILABLE;
        };
        return new DomainException(code, code.defaultMessage(), e);
    }

    static Map<String, Object> schema(ChatRequest request) {
        ResponseFormat format = request.parameters() == null ? null : request.parameters().responseFormat();
        if (format == null || format.type() != ResponseFormatType.JSON || format.jsonSchema() == null) {
            return null;
        }
        return StrictJsonSchema.strict(JsonSchemaElementJsonUtils.toMap(format.jsonSchema().rootElement()));
    }

    /** Outils proposés au modèle : même nom, même description, même schéma de paramètres. */
    static List<ToolSpec> tools(ChatRequest request) {
        if (request.parameters() == null || request.parameters().toolSpecifications() == null) {
            return List.of();
        }
        List<ToolSpec> out = new ArrayList<>();
        for (ToolSpecification t : request.parameters().toolSpecifications()) {
            Map<String, Object> parameters = t.parameters() == null
                    ? Map.of("type", "object", "properties", Map.of())
                    : JsonSchemaElementJsonUtils.toMap(t.parameters());
            out.add(new ToolSpec(t.name(), t.description() == null ? "" : t.description(), parameters));
        }
        return List.copyOf(out);
    }

    /**
     * Message système et message utilisateur d'une requête. Le premier message utilisateur est repris
     * tel quel (déjà balisé et échappé par {@link UserMessages}) ; la suite de l'échange (réponses du
     * modèle, appels et résultats d'outils, reprises demandées) va dans {@code <echange-outils>}.
     */
    record Prompt(String system, String user) {

        static Prompt of(ChatRequest request) {
            List<String> system = new ArrayList<>();
            StringBuilder user = new StringBuilder();
            StringBuilder exchange = new StringBuilder();
            boolean[] first = {true};
            for (ChatMessage m : request.messages()) {
                switch (m) {
                    case SystemMessage s -> system.add(s.text());
                    case UserMessage u when first[0] -> {
                        user.append(u.singleText());
                        first[0] = false;
                    }
                    case UserMessage u -> exchange.append("<correction>").append(UserMessages.data(u.singleText()))
                            .append("</correction>\n");
                    case AiMessage a -> ai(a, exchange);
                    case ToolExecutionResultMessage r -> exchange.append("<resultat id=\"").append(attribute(r.id()))
                            .append("\" outil=\"").append(attribute(r.toolName())).append("\">\n").append(r.text())
                            .append("\n</resultat>\n");
                    default -> throw new IllegalArgumentException("Type de message non pris en charge : " + m.type());
                }
            }
            if (!exchange.isEmpty()) {
                user.append("\n<echange-outils>\n").append(exchange).append("</echange-outils>");
            }
            return new Prompt(String.join("\n\n", system), user.toString());
        }

        private static void ai(AiMessage a, StringBuilder exchange) {
            if (a.text() != null && !a.text().isBlank()) {
                exchange.append("<reponse-precedente>\n").append(UserMessages.data(a.text()))
                        .append("\n</reponse-precedente>\n");
            }
            if (a.hasToolExecutionRequests()) {
                for (ToolExecutionRequest t : a.toolExecutionRequests()) {
                    exchange.append("<appel id=\"").append(attribute(t.id())).append("\" outil=\"")
                            .append(attribute(t.name())).append("\">").append(UserMessages.data(t.arguments()))
                            .append("</appel>\n");
                }
            }
        }

        private static String attribute(String text) {
            return UserMessages.data(text).replace("\"", "&quot;");
        }
    }
}
