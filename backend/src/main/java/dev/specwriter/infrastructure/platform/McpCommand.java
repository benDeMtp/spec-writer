package dev.specwriter.infrastructure.platform;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Commande de la passerelle MCP à déclarer dans un agent (contracts/mcp-tools.md) : le lanceur
 * {@code spec-writer-mcp} de l'application installée, sinon {@code java -cp <jar>
 * dev.specwriter.mcp.McpBridge}. Aucun secret : la passerelle lit le fichier des agents.
 */
public final class McpCommand {

    public static final String MAIN = "dev.specwriter.mcp.McpBridge";
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    public record Command(String command, List<String> args) {

        public String line() {
            List<String> all = new ArrayList<>(List.of(command));
            all.addAll(args);
            return String.join(" ", all.stream().map(McpCommand::quote).toList());
        }

        public String mcpJson() {
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("command", command);
            if (!args.isEmpty()) {
                server.put("args", args);
            }
            return JSON.writeValueAsString(Map.of("mcpServers", Map.of("spec-writer", server)));
        }
    }

    private McpCommand() {
    }

    public static Command current() {
        String launcher = System.getProperty("jpackage.app-path");
        String dataDir = System.getProperty(UserDirectories.DATA_DIR_PROPERTY);
        if (launcher != null) {
            Path app = Path.of(launcher);
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path mcp = app.resolveSibling("spec-writer-mcp" + (windows ? ".exe" : ""));
            if (Files.exists(mcp)) {
                return new Command(mcp.toString(), dataDir == null ? List.of() : List.of("-D" + UserDirectories.DATA_DIR_PROPERTY + "=" + dataDir));
            }
        }
        List<String> args = new ArrayList<>();
        if (dataDir != null) {
            args.add("-D" + UserDirectories.DATA_DIR_PROPERTY + "=" + dataDir);
        }
        args.addAll(List.of("-cp", codeLocation(), MAIN));
        return new Command(Path.of(System.getProperty("java.home"), "bin", "java").toString(), args);
    }

    /**
     * Uber-jar : le jar lui-même. Fast-jar de Quarkus ({@code quarkus-app/app/*.jar}) : le jar de
     * l'application et ses dépendances de {@code quarkus-app/lib/main}.
     */
    private static String codeLocation() {
        try {
            Path code = Path.of(McpCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path app = code.getParent();
            if (app != null && "app".equals(String.valueOf(app.getFileName()))
                    && Files.isDirectory(app.resolveSibling("lib").resolve("main"))) {
                return app.resolve("*") + File.pathSeparator + app.resolveSibling("lib").resolve("main").resolve("*");
            }
            return code.toString();
        } catch (URISyntaxException | RuntimeException e) {
            return "spec-writer-runner.jar";
        }
    }

    private static String quote(String part) {
        return part.matches("[\\w./:=@+*-]+") ? part : "\"" + part.replace("\"", "\\\"") + "\"";
    }
}
