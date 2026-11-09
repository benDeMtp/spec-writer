package dev.specwriter.infrastructure.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.infrastructure.platform.ProxySettings;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Vrai Copilot pour la suite d'évaluation (T137) : le code de production, mais avec le
 * {@code COPILOT_HOME} du Copilot CLI de l'utilisateur, où il s'est connecté par {@code copilot login}.
 */
public final class EvalCopilot implements AutoCloseable {

    private final CopilotRuntime runtime;
    private final Optional<String> cliPath;

    public EvalCopilot(Path copilotHome, Optional<String> cliPath) {
        this.cliPath = cliPath;
        this.runtime = new CopilotRuntime() {
            @Override
            Path copilotHome() {
                return copilotHome;
            }
        };
        runtime.proxy = ProxySettings.fromSystem();
        runtime.cliPath = cliPath;
        runtime.cliMinVersion = CopilotRuntime.CLI_MIN_VERSION;
    }

    /** Appel direct, pour l'utilisateur simulé et le juge. */
    public CopilotSdkGateway gateway() {
        return runtime;
    }

    /** Modèle utilisé par l'application ; {@code model} vide pour le modèle par défaut du compte. */
    public ChatModel chatModel(Optional<String> model) {
        return new CopilotSdkChatModel(runtime, () -> model);
    }

    /** Agent conversationnel (010) : plan par schéma, réponse en flux, comme dans l'application. */
    public ConversationLlm conversationLlm(Optional<String> model) {
        return new LangChainConversationLlm(chatModel(model), new CopilotConversationStream(runtime, () -> model),
                Duration.ofMinutes(3));
    }

    /** Version du Copilot CLI utilisé, telle qu'il l'affiche ; « embarqué » s'il vient du SDK. */
    public String cliVersion() {
        return switch (CopilotCliLocator.fromSystem().resolve(cliPath)) {
            case CopilotCliLocator.Resolution.Bundled _ -> "embarqué";
            case CopilotCliLocator.Resolution.External(Path cli) -> CopilotCliVersion.parse(
                    CopilotRuntime.versionOutput(cli, CopilotCliLocator.childPath(System.getenv(), cli,
                            System.getProperty("os.name", "").startsWith("Windows"))))
                    .map(CopilotCliVersion::toString).orElse("inconnue");
            case CopilotCliLocator.Resolution.Missing _ -> "introuvable";
        };
    }

    /** Schéma JSON strict, comme pour les appels de l'application. */
    public static Map<String, Object> strict(Map<String, Object> schema) {
        return StrictJsonSchema.strict(schema);
    }

    @Override
    public void close() {
        runtime.restart();
    }
}
