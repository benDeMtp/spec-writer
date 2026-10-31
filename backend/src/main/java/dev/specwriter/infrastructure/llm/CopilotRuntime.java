package dev.specwriter.infrastructure.llm;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.CopilotClientOptions;
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
            if (!proxy.isEmpty()) {
                // le Copilot CLI lit HTTPS_PROXY et NO_PROXY, pas les propriétés Java (T139)
                options.setEnvironment(proxy.childEnvironment(System.getenv()));
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
