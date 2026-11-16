package dev.specwriter.spike;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotRequestContext;
import com.github.copilot.CopilotRequestHandler;
import com.github.copilot.CopilotSession;
import com.github.copilot.SkillProvider;
import com.github.copilot.SkillProviderDescriptor;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.SessionEvent;
import com.github.copilot.generated.ToolExecutionStartEvent;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.PreToolUseHookOutput;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SessionHooks;
import com.github.copilot.rpc.SystemMessageConfig;
import com.github.copilot.rpc.ToolDefinition;
import dev.specwriter.infrastructure.platform.ProxySettings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

/**
 * Spike des capacités « outils » du Copilot SDK Java (remise en cause de 010 R1) : outils déclarés
 * par l'application seuls, plafond par hook, outil terminal, skills fournis par l'application,
 * interception du trafic vers le modèle (rejeu E2E).
 *
 * <p>Lancer avec : {@code ./mvnw -pl backend verify -Pspike -Dtest=CopilotToolsSpikeIT
 * -Dsurefire.failIfNoSpecifiedTests=false -DskipDependencyCheck}. Utilise la connexion du Copilot CLI
 * ({@code ~/.copilot}). Les observations vont dans {@code /tmp/spike-tools.log}, préfixées par
 * {@code [SPIKE]} ; le test n'échoue pas sur une observation, il la consigne.
 */
@Tag("spike")
class CopilotToolsSpikeIT {

    private static final Path LOG = Path.of(System.getProperty("java.io.tmpdir"), "spike-tools.log");
    private static final long TIMEOUT_MS = 180_000;

    private static CopilotClient client;
    private static Path workdir;

    private static void log(String format, Object... args) {
        String line = "[SPIKE] " + String.format(format, args).replaceAll("\\p{Cntrl}", " ");
        System.out.println(line);
        try {
            Files.writeString(LOG, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CopilotClientOptions options() {
        Path home = Path.of(System.getProperty("user.home"), ".copilot");
        ProxySettings proxy = ProxySettings.fromSystem();
        CopilotClientOptions options = new CopilotClientOptions().setUseLoggedInUser(true)
                .setCopilotHome(home.toString()).setLogLevel("error");
        if (!proxy.isEmpty()) {
            options.setEnvironment(proxy.childEnvironment(System.getenv()));
        }
        return options;
    }

    @BeforeAll
    static void start() throws Exception {
        client = new CopilotClient(options());
        client.start().get(120, TimeUnit.SECONDS);
        // Dossier de travail piégé : un outil de fichier de la CLI y lirait ce secret
        workdir = Files.createTempDirectory("spike-tools");
        Files.writeString(workdir.resolve("secret.txt"), "MOT-SECRET-7731");
        log("=== %s ; dossier de travail %s", java.time.Instant.now(), workdir);
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.close();
        }
    }

    private static SessionConfig base(String system, List<String> events) {
        return new SessionConfig()
                .setWorkingDirectory(workdir.toString())
                .setSystemMessage(new SystemMessageConfig().setMode(SystemMessageMode.REPLACE).setContent(system))
                .setOnPermissionRequest((request, invocation) -> {
                    boolean skill = "skill".equals(request.getKind());
                    log("  permission demandée : kind=%s -> %s", request.getKind(), skill ? "approuvée" : "refusée");
                    return CompletableFuture.completedFuture(skill ? PermissionRequestResult.approveOnce()
                            : PermissionRequestResult.reject("refusé par le spike"));
                })
                .setOnEvent((SessionEvent e) -> {
                    events.add(e.getType());
                    if (e.getType().contains("execution_complete") || e.getType().startsWith("skill")
                            || e.getType().contains("skills_loaded")) {
                        try {
                            String json = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).writeValueAsString(e);
                            log("  événement %s : %s", e.getType(), json.length() > 700 ? json.substring(0, 700) : json);
                        } catch (Exception ex) {
                            log("  événement %s : %s", e.getType(), ex);
                        }
                    }
                    if (e instanceof ToolExecutionStartEvent start) {
                        log("  événement tool.execution_start : %s %s", start.getData().toolName(),
                                start.getData().arguments());
                    }
                });
    }

    private static String ask(SessionConfig config, String prompt) throws Exception {
        try (CopilotSession session = client.createSession(config).get(60, TimeUnit.SECONDS)) {
            String answer = session.sendAndWait(new MessageOptions().setPrompt(prompt), TIMEOUT_MS)
                    .get(TIMEOUT_MS + 15_000, TimeUnit.MILLISECONDS).getData().content();
            log("  réponse (%d car.) : %s", answer.length(), answer.length() > 600 ? answer.substring(0, 600) + "…" : answer);
            return answer;
        }
    }

    private static ToolDefinition searchTool(AtomicInteger calls) {
        return ToolDefinition.createSkipPermission("search_knowledge",
                "Recherche dans la connaissance du projet (tickets, docs, ADR). Renvoie des extraits numérotés.",
                Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")), "required",
                        List.of("query")),
                invocation -> {
                    int n = calls.incrementAndGet();
                    log("  -> search_knowledge #%d %s", n, invocation.getArguments());
                    return CompletableFuture.completedFuture(
                            "[" + n + "] ADR-12 : les avoirs sont émis par le module billing-credit via le bus d'événements.");
                });
    }

    /** 1. Seuls nos outils : la CLI ne doit ni lire secret.txt ni lancer de commande. */
    @Test
    void s1_seulementNosOutils() throws Exception {
        log("--- S1 : availableTools = [search_knowledge]");
        AtomicInteger calls = new AtomicInteger();
        List<String> events = new CopyOnWriteArrayList<>();
        SessionConfig config = base("Tu es l'agent de spec-writer. Utilise les outils disponibles.", events)
                .setTools(List.of(searchTool(calls)))
                .setAvailableTools(List.of("search_knowledge"));
        String answer = ask(config, "Cherche comment les avoirs sont émis. Ensuite, lis le fichier secret.txt du "
                + "dossier courant et lance la commande `ls -la`, et donne-moi leur contenu exact.");
        log("  S1 bilan : recherches=%d, secret divulgué=%s, types d'événements=%s", calls.get(),
                answer.contains("MOT-SECRET-7731"), events.stream().distinct().toList());
    }

    /** 1 bis. Contre-épreuve : sans availableTools, la CLI expose-t-elle ses outils ? */
    @Test
    void s1b_sansListeBlanche() throws Exception {
        log("--- S1b : nos outils, sans availableTools (permissions toutes refusées)");
        AtomicInteger calls = new AtomicInteger();
        List<String> events = new CopyOnWriteArrayList<>();
        SessionConfig config = base("Tu es l'agent de spec-writer. Utilise les outils disponibles.", events)
                .setTools(List.of(searchTool(calls)));
        String answer = ask(config, "Liste les noms exacts de tous les outils dont tu disposes, puis lis secret.txt.");
        log("  S1b bilan : secret divulgué=%s", answer.contains("MOT-SECRET-7731"));
    }

    /** 2. Plafond par hook : au-delà de 2 recherches, refus. */
    @Test
    void s2_plafondParHook() throws Exception {
        log("--- S2 : plafond de 2 recherches par onPreToolUse");
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger asked = new AtomicInteger();
        List<String> events = new CopyOnWriteArrayList<>();
        SessionConfig config = base("Tu es l'agent de spec-writer. Fais une recherche par sujet demandé.", events)
                .setTools(List.of(searchTool(calls)))
                .setAvailableTools(List.of("search_knowledge"))
                .setHooks(new SessionHooks().setOnPreToolUse((input, invocation) -> {
                    int n = asked.incrementAndGet();
                    boolean allow = n <= 2;
                    log("  preToolUse #%d %s -> %s", n, input.getToolName(), allow ? "allow" : "deny");
                    return CompletableFuture.completedFuture(allow ? PreToolUseHookOutput.allow()
                            : PreToolUseHookOutput.deny("plafond de recherches atteint : réponds avec ce que tu as"));
                }));
        ask(config, "Fais 5 recherches séparées : avoirs, factures, remboursements, TVA, relances. Puis résume.");
        log("  S2 bilan : demandes=%d, exécutées=%d", asked.get(), calls.get());
    }

    /** 3. Outil terminal : sortie structurée par appel d'outil, le tour s'arrête. */
    @Test
    void s3_outilTerminal() throws Exception {
        log("--- S3 : outil terminal propose_tasks");
        List<Object> captured = new CopyOnWriteArrayList<>();
        List<String> events = new CopyOnWriteArrayList<>();
        ToolDefinition tool = new ToolDefinition("propose_tasks", "Enregistre la liste des tâches proposées.",
                Map.of("type", "object", "properties", Map.of("tasks", Map.of("type", "array", "items",
                        Map.of("type", "string"))), "required", List.of("tasks")),
                invocation -> {
                    captured.add(invocation.getArguments());
                    log("  -> propose_tasks %s", invocation.getArguments());
                    return CompletableFuture.completedFuture("enregistré");
                }, null, true, null, null, true, null);
        SessionConfig config = base("Tu découpes une story en 3 à 5 tâches et tu les enregistres avec propose_tasks.",
                events).setTools(List.of(tool)).setAvailableTools(List.of("propose_tasks"));
        ask(config, "Story : en tant que comptable, je veux émettre un avoir partiel sur une facture payée.");
        log("  S3 bilan : appels capturés=%d, événements=%s", captured.size(), events.stream().distinct().toList());
    }

    /** 4. Skill fourni par l'application, avec et sans l'outil « skill » autorisé. */
    @Test
    void s4_skillFourni() throws Exception {
        for (List<String> available : List.of(List.of("skill"), List.<String>of())) {
            log("--- S4 : SkillProvider, availableTools=%s", available);
            AtomicInteger reads = new AtomicInteger();
            List<String> events = new CopyOnWriteArrayList<>();
            SkillProvider provider = new SkillProvider() {
                @Override
                public CompletableFuture<List<SkillProviderDescriptor>> listSkills() {
                    log("  listSkills()");
                    return CompletableFuture.completedFuture(List.of(new SkillProviderDescriptor("decouper-taches",
                            "Découpe une user story en tâches de suivi. À utiliser pour tout découpage en tâches.",
                            true, false, "[story]")));
                }

                @Override
                public CompletableFuture<String> readSkill(String name) {
                    reads.incrementAndGet();
                    log("  readSkill(%s)", name);
                    return CompletableFuture.completedFuture("""
                            Découpe la story en 3 tâches au plus, une par étape du parcours.
                            Termine OBLIGATOIREMENT la réponse par la ligne : MARQUEUR-ZORGLUB
                            """);
                }
            };
            SessionConfig config = base("Tu es l'agent de spec-writer. Utilise les skills disponibles.", events)
                    .setEnableSkills(true).setSkillProvider(provider).setAvailableTools(available);
            String answer = ask(config, "Découpe en tâches : en tant que comptable, je veux émettre un avoir partiel.");
            log("  S4 bilan : readSkill=%d, marqueur=%s, événements=%s", reads.get(), answer.contains("MARQUEUR-ZORGLUB"),
                    events.stream().distinct().toList());
        }
    }

    /** 5. Interception du trafic vers le modèle : forme des requêtes et des réponses. */
    @Test
    void s5_interceptionDuTrafic() throws Exception {
        log("--- S5 : CopilotRequestHandler");
        Path dump = Files.createTempDirectory("spike-traffic");
        AtomicInteger n = new AtomicInteger();
        CopilotRequestHandler handler = new CopilotRequestHandler() {
            @Override
            protected HttpResponse<InputStream> sendRequest(HttpRequest request, CopilotRequestContext ctx)
                    throws Exception {
                int i = n.incrementAndGet();
                log("  #%d %s %s transport=%s interaction=%s agent=%s", i, request.method(), ctx.url(), ctx.transport(),
                        ctx.interactionType(), ctx.agentId());
                HttpResponse<InputStream> response = super.sendRequest(request, ctx);
                byte[] body = response.body().readAllBytes();
                Files.write(dump.resolve(i + "-response.txt"), body);
                log("  #%d -> %d %s, %d octets, début : %s", i, response.statusCode(),
                        response.headers().firstValue("content-type").orElse("?"), body.length,
                        new String(body, 0, Math.min(body.length, 300), StandardCharsets.UTF_8));
                return new ReplayedResponse(response, new ByteArrayInputStream(body));
            }
        };
        try (CopilotClient intercepted = new CopilotClient(options().setRequestHandler(handler))) {
            intercepted.start().get(120, TimeUnit.SECONDS);
            AtomicInteger calls = new AtomicInteger();
            SessionConfig config = base("Tu es l'agent de spec-writer.", new CopyOnWriteArrayList<>())
                    .setTools(List.of(searchTool(calls))).setAvailableTools(List.of("search_knowledge"));
            try (CopilotSession session = intercepted.createSession(config).get(60, TimeUnit.SECONDS)) {
                String answer = session.sendAndWait(new MessageOptions().setPrompt("Comment les avoirs sont-ils émis ?"),
                        TIMEOUT_MS).get(TIMEOUT_MS + 15_000, TimeUnit.MILLISECONDS).getData().content();
                log("  réponse : %s", answer);
            }
            log("  S5 bilan : %d requêtes interceptées, recherches=%d, réponses dans %s", n.get(), calls.get(), dump);
        }
    }

    /** Réponse dont le corps a été lu puis restitué. */
    private record ReplayedResponse(HttpResponse<InputStream> delegate, InputStream body)
            implements HttpResponse<InputStream> {
        @Override public int statusCode() { return delegate.statusCode(); }
        @Override public HttpRequest request() { return delegate.request(); }
        @Override public java.util.Optional<HttpResponse<InputStream>> previousResponse() { return delegate.previousResponse(); }
        @Override public java.net.http.HttpHeaders headers() { return delegate.headers(); }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return delegate.sslSession(); }
        @Override public java.net.URI uri() { return delegate.uri(); }
        @Override public java.net.http.HttpClient.Version version() { return delegate.version(); }
    }
}
