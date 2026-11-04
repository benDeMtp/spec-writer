package dev.specwriter.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Passerelle MCP stdio de spec-writer (research.md R1, contracts/mcp-tools.md), lancée par l'agent de
 * code : {@code java -cp spec-writer-runner.jar dev.specwriter.mcp.McpBridge}, ou le lanceur
 * {@code spec-writer-mcp}. Sans Quarkus ni CDI : elle ne parle à l'outil que par HTTP, avec le
 * secret de {@code agent-endpoint.json}. stdout ne porte que du JSON-RPC ; stderr, des diagnostics
 * sans secret ni contenu.
 */
public final class McpBridge {

    static final String PROTOCOL = "2025-06-18";
    static final String DATA_DIR_PROPERTY = "specwriter.data-dir";
    static final int MAX_AGENT_NAME = 80;

    private final BufferedReader in;
    private final JsonRpc rpc;
    private final PrintStream err;
    private final Tools tools;
    private volatile String agentName = "agent sans nom";

    McpBridge(BufferedReader in, PrintStream out, PrintStream err, Path dataDir) {
        this.in = in;
        this.rpc = new JsonRpc(out);
        this.err = err;
        this.tools = new Tools(dataDir.resolve("agent-endpoint.json"), () -> agentName);
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        // rien d'autre que du JSON-RPC sur stdout, même d'une bibliothèque
        System.setOut(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new McpBridge(in, out, System.err, dataDir(args)).run();
    }

    void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    handle(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handle(String line) {
        JsonNode message;
        try {
            message = JsonRpc.JSON.readTree(line);
        } catch (RuntimeException e) {
            rpc.error(null, JsonRpc.PARSE_ERROR, "Message JSON illisible.");
            return;
        }
        if (!message.isObject() || !message.path("method").isString()) {
            rpc.error(message.get("id"), JsonRpc.INVALID_REQUEST, "Requête JSON-RPC invalide.");
            return;
        }
        String method = message.path("method").asString();
        JsonNode id = message.get("id");
        if (id == null || id.isNull()) {
            // notification (notifications/initialized, notifications/cancelled…) : jamais de réponse
            return;
        }
        JsonNode params = message.path("params");
        switch (method) {
            case "initialize" -> initialize(id, params);
            case "ping" -> rpc.result(id, JsonRpc.JSON.createObjectNode());
            case "tools/list" -> {
                ObjectNode result = JsonRpc.JSON.createObjectNode();
                result.set("tools", Tools.definitions());
                rpc.result(id, result);
            }
            case "tools/call" -> call(id, params);
            default -> rpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Méthode inconnue : " + method);
        }
    }

    private void initialize(JsonNode id, JsonNode params) {
        String name = params.path("clientInfo").path("name").asString("").replaceAll("\\p{Cntrl}", " ").strip();
        if (!name.isEmpty()) {
            agentName = name.length() > MAX_AGENT_NAME ? name.substring(0, MAX_AGENT_NAME) : name;
        }
        ObjectNode result = JsonRpc.JSON.createObjectNode();
        result.put("protocolVersion", PROTOCOL);
        result.putObject("capabilities").putObject("tools");
        ObjectNode server = result.putObject("serverInfo");
        server.put("name", "spec-writer");
        server.put("version", version());
        result.put("instructions", "Features à implémenter et connaissance du projet de spec-writer." + Tools.DATA_NOTICE);
        rpc.result(id, result);
    }

    private void call(JsonNode id, JsonNode params) {
        String name = params.path("name").asString("");
        JsonNode args = params.path("arguments");
        ObjectNode result = JsonRpc.JSON.createObjectNode();
        String text;
        boolean error;
        try {
            text = tools.call(name, args.isObject() ? args : JsonRpc.JSON.createObjectNode());
            error = false;
        } catch (Tools.ToolError e) {
            text = e.getMessage();
            error = true;
            // le diagnostic garde le code seulement : ni secret ni contenu
            err.println("spec-writer-mcp : " + name + " en erreur (" + text.split(" ", 2)[0] + ")");
        }
        result.putArray("content").addObject().put("type", "text").put("text", text);
        result.put("isError", error);
        rpc.result(id, result);
    }

    private static String version() {
        String v = McpBridge.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    /**
     * Dossier de données, résolu comme l'outil (UserDirectories) sans en dépendre : la propriété
     * {@code specwriter.data-dir} (ou l'argument {@code -Dspecwriter.data-dir=…} d'un lanceur
     * natif), sinon le dossier standard de l'OS.
     */
    static Path dataDir(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("-D" + DATA_DIR_PROPERTY + "=")) {
                return Path.of(arg.substring(DATA_DIR_PROPERTY.length() + 3));
            }
        }
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("mac") || os.startsWith("darwin")) {
            return Path.of(home, "Library", "Application Support", "spec-writer");
        }
        if (os.startsWith("windows")) {
            String local = System.getenv("LOCALAPPDATA");
            Path base = local != null && !local.isBlank() ? Path.of(local) : Path.of(home, "AppData", "Local");
            return base.resolve("spec-writer").resolve("data");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        return (xdg != null && xdg.startsWith("/") ? Path.of(xdg) : Path.of(home, ".local", "share")).resolve("spec-writer");
    }
}
