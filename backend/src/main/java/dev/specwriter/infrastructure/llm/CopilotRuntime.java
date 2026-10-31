package dev.specwriter.infrastructure.llm;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.CopilotClientOptions;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.infrastructure.platform.ProxySettings;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import dev.specwriter.infrastructure.llm.CopilotSdkGateway.GatewayException.Kind;
import dev.specwriter.infrastructure.platform.UserDirectories;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Runtime Copilot partagé (spike S1, research.md R4) : un seul {@link CopilotClient}, avec un
 * {@code COPILOT_HOME} propre à l'outil dans son dossier de données ({@code rwx------}), pour que
 * le compte connecté dans l'outil soit prioritaire sur la CLI {@code gh}.
 */
@ApplicationScoped
public class CopilotRuntime implements CopilotSdkGateway {

    private static final Logger LOG = Logger.getLogger(CopilotRuntime.class);
    private static final long CALL_TIMEOUT_SECONDS = 180;

    @ConfigProperty(name = "specwriter.data-dir.resolved")
    String dataDir;

    @Inject
    ProxySettings proxy;

    /** Chemin du Copilot CLI imposé ; sinon {@code COPILOT_CLI_PATH}, puis la recherche de CopilotCliLocator. */
    @ConfigProperty(name = "specwriter.copilot.cli-path")
    Optional<String> cliPath;

    /** Version du CLI embarqué par le SDK utilisé : un CLI plus ancien ne comprend pas son protocole. */
    @ConfigProperty(name = "specwriter.copilot.cli-min-version", defaultValue = "1.0.105")
    String cliMinVersion;

    private CopilotClient client;

    /** Client démarré à la demande ; partagé avec l'authentification GitHub. */
    public synchronized CopilotClient client() {
        if (client == null) {
            Path home = UserDirectories.createPrivateDirectories(Path.of(dataDir, "copilot"),
                    UserDirectories.fromSystem().posix());
            CopilotClientOptions options = new CopilotClientOptions()
                    .setUseLoggedInUser(true)
                    .setCopilotHome(home.toString())
                    .setLogLevel("error");
            // le Copilot CLI lit HTTPS_PROXY et NO_PROXY, pas les propriétés Java (T139)
            Map<String, String> env = proxy.isEmpty() ? System.getenv() : proxy.childEnvironment(System.getenv());
            switch (CopilotCliLocator.fromSystem().resolve(cliPath)) {
                case CopilotCliLocator.Resolution.Bundled _ -> {
                    if (!proxy.isEmpty()) {
                        options.setEnvironment(env);
                    }
                }
                case CopilotCliLocator.Resolution.External(Path cli) -> {
                    Map<String, String> cliEnv = CopilotCliLocator.childPath(env, cli, isWindows());
                    CopilotCliVersion.require(versionOutput(cli, cliEnv), cliMinVersion);
                    LOG.infof("Copilot CLI : %s", cli);
                    options.setCliPath(cli.toString()).setEnvironment(cliEnv);
                }
                case CopilotCliLocator.Resolution.Missing(List<Path> searched) -> {
                    LOG.warnf("Copilot CLI introuvable ; chemins examinés : %s", searched);
                    throw new DomainException(ErrorCode.COPILOT_CLI_MISSING);
                }
            }
            CopilotClient c = new CopilotClient(options);
            try {
                c.start().get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                c.close();
                throw classify(e);
            }
            client = c;
        }
        return client;
    }

    /** Sortie de {@code copilot --version}, vide si le CLI ne répond pas en 20 s. */
    private static String versionOutput(Path cli, Map<String, String> env) {
        ProcessBuilder pb = new ProcessBuilder(cli.toString(), "--version").redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().putAll(env);
        try {
            Process process = pb.start();
            byte[] out = process.getInputStream().readNBytes(4096);
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return new String(out, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            LOG.warnf("Version du Copilot CLI illisible (%s)", e.getClass().getSimpleName());
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * Redémarre le client. Un client démarré avant la connexion garde une liste de modèles vide
     * après celle-ci (spike S1) : il est relancé dès que la connexion aboutit.
     */
    public synchronized void restart() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /** Session sans outil : permissions toujours refusées (contracts/llm-port.md, règle 6a). */
    public static SessionConfig toolLessSession(String model, String systemPrompt) {
        SessionConfig config = new SessionConfig()
                .setAvailableTools(List.of())
                .setOnPermissionRequest((request, invocation) ->
                        CompletableFuture.completedFuture(PermissionRequestResult.reject("aucun outil autorisé")));
        if (systemPrompt != null) {
            config.setSystemMessage(new SystemMessageConfig().setMode(SystemMessageMode.REPLACE).setContent(systemPrompt));
        }
        if (model != null) {
            config.setModel(model);
        }
        return config;
    }

    @Override
    public String complete(String model, String systemPrompt, String userPrompt, Map<String, Object> responseSchema) {
        CopilotClient c = client();
        String sessionId = null;
        try (CopilotSession session = c.createSession(toolLessSession(model, systemPrompt)).get(60, TimeUnit.SECONDS)) {
            sessionId = session.getSessionId();
            MessageOptions message = new MessageOptions().setPrompt(userPrompt);
            if (responseSchema != null) {
                message.setResponseSchema(responseSchema);
            }
            AssistantMessageEvent reply = session.sendAndWait(message).get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return reply.getData().content();
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw classify(e);
        } finally {
            deleteQuietly(c, sessionId);
        }
    }

    /** Le SDK écrit la conversation sur disque : elle est supprimée après chaque appel (FR-022). */
    private static void deleteQuietly(CopilotClient c, String sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            c.deleteSession(sessionId).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warnf("Session Copilot non supprimée (%s)", e.getClass().getSimpleName());
        }
    }

    /**
     * Classe une erreur du SDK d'après son message, sans le journaliser (il peut citer la requête).
     * Les libellés ont été observés pendant le spike S1 ; un message inconnu donne UNAVAILABLE.
     */
    public static GatewayException classify(Throwable error) {
        Throwable root = error;
        if (root instanceof ExecutionException && root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        String message = String.valueOf(root.getMessage()).toLowerCase(Locale.ROOT);
        Kind kind;
        if (root instanceof TimeoutException) {
            kind = Kind.UNAVAILABLE;
        } else if (message.contains("unhandled method")) {
            kind = Kind.CLI_OUTDATED;
        } else if (message.contains("not authenticated") || message.contains("unauthorized") || message.contains(" 401")
                || message.contains("login required") || message.contains("no auth")) {
            kind = Kind.NOT_CONNECTED;
        } else if (message.contains("quota") || message.contains(" 429") || message.contains("rate limit")
                || message.contains(" 402")) {
            kind = Kind.QUOTA_EXHAUSTED;
        } else if (message.contains("model") && (message.contains("not supported") || message.contains("not available")
                || message.contains("disabled") || message.contains("policy") || message.contains("not enabled")
                || message.contains("unknown"))) {
            kind = Kind.MODEL_UNAVAILABLE;
        } else {
            kind = Kind.UNAVAILABLE;
        }
        return new GatewayException(kind, kind.name(), root);
    }

    synchronized void onStop(@Observes ShutdownEvent event) {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
