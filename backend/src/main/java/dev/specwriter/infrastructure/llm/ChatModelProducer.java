package dev.specwriter.infrastructure.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.specwriter.domain.port.ConversationLlm;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Modèle par défaut des AI Services : Copilot, ou le rejeu en E2E (research.md R3, R11). */
@ApplicationScoped
public class ChatModelProducer {

    @ConfigProperty(name = "specwriter.llm.replay-dir")
    Optional<String> replayDir;

    @Inject
    CopilotRuntime runtime;

    @Inject
    ModelSelection modelSelection;

    @ConfigProperty(name = "specwriter.llm.timeout.interactive", defaultValue = "180s")
    Duration timeout;

    @Produces
    @ApplicationScoped
    ChatModel chatModel() {
        if (replayDir.filter(d -> !d.isBlank()).isPresent()) {
            return new ReplayChatModel(Path.of(replayDir.get()));
        }
        return new CopilotSdkChatModel(runtime, modelSelection);
    }

    /** Agent conversationnel (010) : même modèle pour le plan, flux de Copilot ou rejeu pour la réponse. */
    @Produces
    @ApplicationScoped
    ConversationLlm conversationLlm(ChatModel model) {
        ConversationStream stream = replayDir.filter(d -> !d.isBlank()).isPresent()
                ? new ReplayConversationStream(new ReplayChatModel(Path.of(replayDir.get())))
                : new CopilotConversationStream(runtime, modelSelection);
        return new LangChainConversationLlm(model, stream, timeout);
    }
}
