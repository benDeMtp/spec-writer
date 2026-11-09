package dev.specwriter.infrastructure.llm;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Flux de Copilot, sur le modèle choisi ; erreurs du SDK converties en erreurs métier. */
final class CopilotConversationStream implements ConversationStream {

    private final CopilotSdkGateway gateway;
    private final ModelSelection modelSelection;

    CopilotConversationStream(CopilotSdkGateway gateway, ModelSelection modelSelection) {
        this.gateway = gateway;
        this.modelSelection = modelSelection;
    }

    @Override
    public String stream(String system, String user, Consumer<String> deltas, BooleanSupplier cancelled) {
        try {
            return gateway.stream(modelSelection.currentModel().orElse(null), system, user, deltas, cancelled);
        } catch (CopilotSdkGateway.GatewayException e) {
            throw CopilotSdkChatModel.toDomain(e);
        }
    }
}
