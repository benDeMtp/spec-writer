package dev.specwriter.infrastructure.llm;

import dev.langchain4j.model.chat.ChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.nio.file.Path;
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

    @Produces
    @ApplicationScoped
    ChatModel chatModel() {
        if (replayDir.filter(d -> !d.isBlank()).isPresent()) {
            return new ReplayChatModel(Path.of(replayDir.get()));
        }
        return new CopilotSdkChatModel(runtime, modelSelection);
    }
}
