package fr.kawamind.infrastructure.llm;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.AssistantMessageDeltaEvent;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.generated.AssistantReasoningDeltaEvent;
import com.github.copilot.generated.AssistantReasoningEvent;
import com.github.copilot.generated.SessionErrorEvent;
import com.github.copilot.generated.SessionIdleEvent;
import com.github.copilot.rpc.CopilotClientOptions;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.infrastructure.platform.ProxySettings;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.ModelInfo;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import com.github.copilot.rpc.ToolDefinition;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.GatewayException.Kind;
import fr.kawamind.infrastructure.platform.UserDirectories;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.Closeable;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import fr.kawamind.infrastructure.logging.SafeThrowable;
import org.jboss.logging.Logger;

/**
 * Runtime Copilot partagé (spike S1, research.md R4) : un seul {@link CopilotClient}, avec un
 * {@code COPILOT_HOME} propre à l'outil dans son dossier de données ({@code rwx------}), pour que
 * le compte connecté dans l'outil soit prioritaire sur la CLI {@code gh}.
 */
@ApplicationScoped
public class CopilotRuntime implements CopilotSdkGateway {

    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    private static final Logger LOG = Logger.getLogger(CopilotRuntime.class);
    /** Marge laissée au SDK pour signaler lui-même son délai dépassé. */
    private static final long SDK_MARGIN_SECONDS = 15;

    /** Version du CLI embarqué par le SDK utilisé : un CLI plus ancien ne comprend pas son protocole. */
    static final String CLI_MIN_VERSION = "1.0.105";

    @ConfigProperty(name = "specwriter.data-dir.resolved")
    String dataDir;

    @Inject
    ProxySettings proxy;

    /** Chemin du Copilot CLI imposé ; sinon {@code COPILOT_CLI_PATH}, puis la recherche de CopilotCliLocator. */
    @ConfigProperty(name = "specwriter.copilot.cli-path")
    Optional<String> cliPath;

    @ConfigProperty(name = "specwriter.copilot.cli-min-version", defaultValue = CLI_MIN_VERSION)
    String cliMinVersion;

    /**
     * Intensité de réflexion demandée quand l'écran montre la réflexion de l'agent (010) ; sans elle,
     * le modèle n'en transmet aucune. Ignorée pour un modèle qui ne la propose pas.
     */
    @ConfigProperty(name = "specwriter.copilot.reasoning-effort", defaultValue = "high")
    String reasoningEffort;

    private CopilotClient client;
    /** Intensités de réflexion proposées par modèle, lues une fois par client. */
    private Map<String, List<String>> efforts;

    /** Client démarré à la demande ; partagé avec l'authentification GitHub. */
    public synchronized CopilotClient client() {
        if (client == null) {
            CopilotClientOptions options = new CopilotClientOptions()
                    .setUseLoggedInUser(true)
                    .setCopilotHome(copilotHome().toString())
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
            long start = System.nanoTime();
            try {
                c.start().get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                c.close();
                throw classify(e, "le démarrage du Copilot CLI, après " + elapsedMs(start) + " ms (délai 120 s)");
            }
            client = c;
        }
        return client;
    }

    /**
     * {@code COPILOT_HOME} imposé (évaluation réelle : celui du Copilot CLI de l'utilisateur, où il
     * s'est connecté par {@code copilot login}) ; il n'est ni créé ni modifié.
     */
    @ConfigProperty(name = "specwriter.copilot.home")
    Optional<String> home = Optional.empty();

    /** {@code COPILOT_HOME} imposé, sinon propre à l'outil, créé à la demande en {@code rwx------}. */
    Path copilotHome() {
        if (home.filter(h -> !h.isBlank()).isPresent()) {
            return Path.of(home.get());
        }
        return UserDirectories.createPrivateDirectories(Path.of(dataDir, "copilot"), UserDirectories.fromSystem().posix());
    }

    /** Sortie de {@code copilot --version}, vide si le CLI ne répond pas en 20 s. */
    static String versionOutput(Path cli, Map<String, String> env) {
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
        efforts = null;
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /** Accusé renvoyé au CLI pour un outil terminal : le tour s'arrête, l'appel est rendu au framework. */
    static final String CAPTURED = "appel transmis";

    /**
     * Session verrouillée (014, research R8) : seuls les outils de la requête sont disponibles, toute
     * permission est refusée, ni skills ni consignes de dépôt, dossier de travail vide.
     */
    public static SessionConfig lockedSession(String model, String systemPrompt, List<ToolDefinition> tools,
            Path workingDirectory) {
        SessionConfig config = new SessionConfig()
                .setAvailableTools(tools.stream().map(ToolDefinition::name).toList())
                .setOnPermissionRequest((request, invocation) ->
                        CompletableFuture.completedFuture(PermissionRequestResult.reject("aucune permission accordée")))
                .setEnableSkills(false)
                .setSkipCustomInstructions(true)
                .setEnableConfigDiscovery(false);
        if (!tools.isEmpty()) {
            config.setTools(List.copyOf(tools));
        }
        if (workingDirectory != null) {
            config.setWorkingDirectory(workingDirectory.toString());
        }
        if (systemPrompt != null) {
            config.setSystemMessage(new SystemMessageConfig().setMode(SystemMessageMode.REPLACE).setContent(systemPrompt));
        }
        if (model != null) {
            config.setModel(model);
        }
        return config;
    }

    /**
     * Outil terminal sans permission (research R2) : son appel est capturé avec ses arguments, et le
     * tour s'arrête ; rien n'est exécuté par le CLI.
     */
    static ToolDefinition terminal(ToolSpec spec, List<CapturedCall> captured) {
        return new ToolDefinition(spec.name(), spec.description(), spec.parameters(), invocation -> {
            captured.add(new CapturedCall(invocation.getToolCallId(), invocation.getToolName() == null ? spec.name()
                    : invocation.getToolName(), arguments(invocation.getArguments())));
            return CompletableFuture.completedFuture(CAPTURED);
        }, null, true, null, null, true, null);
    }

    private static String arguments(Map<String, Object> arguments) {
        return JSON.writeValueAsString(arguments == null ? Map.of() : arguments);
    }

    /** Dossier de travail des sessions : privé et vide, rien à y lire (research R8). */
    public Path emptyDirectory() {
        return UserDirectories.createPrivateDirectories(Path.of(dataDir, "copilot", "empty"),
                UserDirectories.fromSystem().posix());
    }

    /**
     * Un appel au modèle dans une session neuve et verrouillée (014, research R2, R8). Les outils sont
     * terminaux : un appel du modèle est capturé et termine le tour. En flux, chaque
     * {@code assistant.message_delta} est transmis et la fin arrive avec {@code session.idle} ; un arrêt
     * demandé abandonne la requête. La session est supprimée dans tous les cas.
     */
    @Override
    public Reply call(Call call) {
        CopilotClient c = client();
        String sessionId = null;
        String phase = "la création de la session";
        long start = System.nanoTime();
        Duration timeout = CallTimeout.current();
        CopilotSession session = null;
        String model = call.model();
        List<CapturedCall> captured = new CopyOnWriteArrayList<>();
        try {
            Optional<Consumer<String>> reasoning = reasoning(model, call.reasoning());
            SessionConfig config = lockedSession(model, call.system(),
                    call.tools().stream().map(t -> terminal(t, captured)).toList(), emptyDirectory());
            reasoning.ifPresent(r -> withReasoning(config));
            if (call.deltas() != null) {
                config.setStreaming(true);
            }
            session = c.createSession(config).get(60, TimeUnit.SECONDS);
            sessionId = session.getSessionId();
            MessageOptions message = new MessageOptions().setPrompt(call.user());
            if (call.schema() != null) {
                message.setResponseSchema(call.schema());
            }
            phase = "l'envoi du message";
            String text;
            try (Closeable _ = listen(session, reasoning)) {
                text = call.deltas() == null ? whole(session, message, timeout)
                        : streamed(session, message, call, timeout);
            } catch (InterruptedException e) {
                // génération interrompue : la requête en cours est abandonnée côté CLI
                abortQuietly(session);
                throw e;
            }
            return captured.isEmpty() ? new Reply(text, List.of()) : new Reply("", List.copyOf(captured));
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw classify(e, phase + ", modèle " + (model == null ? "par défaut" : model) + ", après " + elapsedMs(start)
                    + " ms (délai " + (sessionId == null ? 60 : timeout.toSeconds()) + " s)");
        } finally {
            closeQuietly(session);
            deleteQuietly(c, sessionId);
        }
    }

    /** Réponse d'un bloc ; sans délai explicite, sendAndWait abandonne au bout de 60 s, quel que soit le get(). */
    private static String whole(CopilotSession session, MessageOptions message, Duration timeout) throws Exception {
        AssistantMessageEvent reply = session.sendAndWait(message, timeout.toMillis())
                .get(timeout.toSeconds() + SDK_MARGIN_SECONDS, TimeUnit.SECONDS);
        return reply == null || reply.getData() == null || reply.getData().content() == null ? ""
                : reply.getData().content();
    }

    /** Réponse en flux (010, research R2) ; l'arrêt demandé abandonne la requête et garde le texte reçu. */
    private static String streamed(CopilotSession session, MessageOptions message, Call call, Duration timeout)
            throws Exception {
        StringBuilder text = new StringBuilder();
        CompletableFuture<Void> idle = new CompletableFuture<>();
        try (var _ = session.on(AssistantMessageDeltaEvent.class, e -> {
                    String delta = e.getData() == null ? null : e.getData().deltaContent();
                    if (delta != null && !delta.isEmpty() && !idle.isDone()) {
                        text.append(delta);
                        call.deltas().accept(delta);
                    }
                });
                var _ = session.on(SessionIdleEvent.class, e -> idle.complete(null));
                var _ = session.on(SessionErrorEvent.class, e -> idle.completeExceptionally(
                        new IllegalStateException(e.getData() == null ? "session.error" : e.getData().errorType())))) {
            session.send(message).get(60, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + timeout.toNanos();
            while (!idle.isDone()) {
                if (call.cancelled().getAsBoolean() || Thread.currentThread().isInterrupted()) {
                    abortQuietly(session);
                    break;
                }
                if (System.nanoTime() > deadline) {
                    abortQuietly(session);
                    throw new TimeoutException("réponse en flux");
                }
                try {
                    idle.get(200, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    // on vérifie l'arrêt demandé, puis on attend encore
                    continue;
                }
            }
        }
        return text.toString();
    }

    /** Destinataire de la réflexion, si l'appel la demande et si le modèle sait la donner. */
    private Optional<Consumer<String>> reasoning(String model, Consumer<String> sink) {
        if (sink == null || model == null || !efforts().getOrDefault(model, List.of()).contains(reasoningEffort)) {
            return Optional.empty();
        }
        return Optional.of(sink);
    }

    private void withReasoning(SessionConfig config) {
        config.setStreaming(true).setReasoningEffort(reasoningEffort).setReasoningSummary("detailed");
    }

    /**
     * Transmet la réflexion du modèle : ses morceaux au fil de l'eau ; une réflexion arrivée d'un bloc,
     * sans morceaux, est transmise entière.
     */
    private static Closeable listen(CopilotSession session, Optional<Consumer<String>> reasoning) {
        if (reasoning.isEmpty()) {
            return () -> { };
        }
        Consumer<String> sink = reasoning.get();
        Set<String> streamed = ConcurrentHashMap.newKeySet();
        Closeable deltas = session.on(AssistantReasoningDeltaEvent.class, e -> {
            if (e.getData() != null && e.getData().deltaContent() != null && !e.getData().deltaContent().isEmpty()) {
                streamed.add(String.valueOf(e.getData().reasoningId()));
                sink.accept(e.getData().deltaContent());
            }
        });
        Closeable whole = session.on(AssistantReasoningEvent.class, e -> {
            if (e.getData() != null && e.getData().content() != null
                    && !streamed.contains(String.valueOf(e.getData().reasoningId()))) {
                sink.accept(e.getData().content());
            }
        });
        return () -> {
            try (whole) {
                deltas.close();
            }
        };
    }

    /** Intensités de réflexion de chaque modèle ; vide si la liste des modèles est illisible. */
    private synchronized Map<String, List<String>> efforts() {
        if (efforts == null) {
            Map<String, List<String>> read = new HashMap<>();
            try {
                for (ModelInfo m : client().listModels().get(60, TimeUnit.SECONDS)) {
                    if (m.getSupportedReasoningEfforts() != null) {
                        read.put(m.getId(), List.copyOf(m.getSupportedReasoningEfforts()));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Map.of();
            } catch (Exception e) {
                LOG.warnf("Intensités de réflexion illisibles (%s) : réflexion non demandée", e.getClass().getSimpleName());
            }
            efforts = Map.copyOf(read);
        }
        return efforts;
    }

    /**
     * La fermeture de session expire parfois alors que la réponse est arrivée : elle ne doit pas
     * changer un appel réussi en échec (la session est de toute façon supprimée ensuite).
     */
    private static void closeQuietly(CopilotSession session) {
        if (session == null) {
            return;
        }
        boolean interrupted = Thread.interrupted();
        try {
            session.close();
        } catch (Exception e) {
            LOG.debugf("Session Copilot non fermée (%s)", e.getClass().getSimpleName());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void abortQuietly(CopilotSession session) {
        try {
            session.abort().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.debugf("Requête Copilot non abandonnée (%s)", e.getClass().getSimpleName());
        }
    }

    /**
     * Le SDK écrit la conversation sur disque : elle est supprimée après chaque appel (FR-022), même
     * quand l'appel a été interrompu ; l'interruption est rétablie ensuite.
     */
    private static void deleteQuietly(CopilotClient c, String sessionId) {
        if (sessionId == null) {
            return;
        }
        boolean interrupted = Thread.interrupted();
        try {
            c.deleteSession(sessionId).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                interrupted = true;
            }
            LOG.warnf("Session Copilot non supprimée (%s)", e.getClass().getSimpleName());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Classe une erreur du SDK d'après son message, sans le journaliser (il peut citer la requête).
     * Les libellés ont été observés pendant le spike S1 ; un message inconnu donne UNAVAILABLE.
     */
    public static GatewayException classify(Throwable error) {
        return classify(error, null);
    }

    /**
     * Comme {@link #classify(Throwable)}, avec la circonstance de l'échec (phase, modèle, durée), écrite
     * par l'outil. La cause gardée est une {@link SafeThrowable} : classes et piles, sans message.
     */
    public static GatewayException classify(Throwable error, String during) {
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
        return new GatewayException(kind, during == null ? kind.name() : kind.name() + " pendant " + during,
                SafeThrowable.of(root));
    }

    synchronized void onStop(@Observes ShutdownEvent event) {
        efforts = null;
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
