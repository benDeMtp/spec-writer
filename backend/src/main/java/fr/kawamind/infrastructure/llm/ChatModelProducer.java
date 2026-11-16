package fr.kawamind.infrastructure.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.infrastructure.llm.ai.SpecificationAi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Modèles des AI Services : Copilot, ou le rejeu en test et en E2E (research.md R3, R11 ; 014 R2, R6). */
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

    /** Journal des appels, partagé par les modèles (014, research R4). */
    private final LlmCallLog log = new LlmCallLog();
    private ReplayChatModel replay;

    @Produces
    @ApplicationScoped
    ChatModel chatModel() {
        if (replaying()) {
            return replay();
        }
        return new CopilotSdkChatModel(runtime, modelSelection, List.of(log));
    }

    /** Modèle en flux de l'agent (014, research R5) : Copilot, ou le rejeu. */
    @Produces
    @ApplicationScoped
    StreamingChatModel streamingChatModel() {
        if (replaying()) {
            return new ReplayStreamingChatModel(replay(), List.of(log));
        }
        return new CopilotSdkStreamingChatModel(runtime, modelSelection, List.of(log));
    }

    private boolean replaying() {
        return replayDir.filter(d -> !d.isBlank()).isPresent();
    }

    private synchronized ReplayChatModel replay() {
        if (replay == null) {
            replay = new ReplayChatModel(Path.of(replayDir.get()), List.of(log));
        }
        return replay;
    }

    /** Agent conversationnel (010) : même modèle pour le plan, flux de Copilot ou rejeu pour la réponse. */
    @Produces
    @ApplicationScoped
    ConversationLlm conversationLlm(ChatModel model, SpecificationAi specification) {
        ConversationStream stream = replaying()
                ? new ReplayConversationStream(replay())
                : new CopilotConversationStream(runtime, modelSelection);
        return new LangChainConversationLlm(model, stream, specification, timeout);
    }
}
