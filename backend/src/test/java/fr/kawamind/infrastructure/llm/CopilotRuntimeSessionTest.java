package fr.kawamind.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.copilot.SystemMessageMode;
import com.github.copilot.rpc.PermissionRequest;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.ToolDefinition;
import com.github.copilot.rpc.ToolInvocation;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.CapturedCall;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.ToolSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Session Copilot verrouillée (014, US2, research R8, contracts/llm-adapter.md) : chaque réglage
 * obligatoire, pour une requête sans outil et avec outils.
 */
class CopilotRuntimeSessionTest {

    private static final Path EMPTY = Path.of("/donnees/copilot/empty");

    private static ToolDefinition tool(String name, List<CapturedCall> captured) {
        return CopilotRuntime.terminal(new ToolSpec(name, "Outil " + name, Map.of("type", "object", "properties",
                Map.of("query", Map.of("type", "string")))), captured);
    }

    @Test
    void sansOutilAucunOutilDisponible() {
        SessionConfig config = CopilotRuntime.lockedSession("claude-sonnet-5.5", "[operation:x] consignes", List.of(),
                EMPTY);
        assertThat(config.getAvailableTools()).isEmpty();
        assertThat(config.getTools()).isNullOrEmpty();
        assertLocked(config);
        assertThat(config.getModel()).isEqualTo("claude-sonnet-5.5");
        assertThat(config.getSystemMessage().getMode()).isEqualTo(SystemMessageMode.REPLACE);
        assertThat(config.getSystemMessage().getContent()).isEqualTo("[operation:x] consignes");
    }

    @Test
    void avecOutilsExactementLeursNoms() {
        List<CapturedCall> captured = new ArrayList<>();
        SessionConfig config = CopilotRuntime.lockedSession(null, "s", List.of(tool("search_knowledge", captured),
                tool("analyze_perimeter", captured)), EMPTY);
        assertThat(config.getAvailableTools()).containsExactly("search_knowledge", "analyze_perimeter");
        assertThat(config.getTools()).extracting(ToolDefinition::name)
                .containsExactly("search_knowledge", "analyze_perimeter");
        assertLocked(config);
    }

    private static void assertLocked(SessionConfig config) {
        PermissionRequestResult result = config.getOnPermissionRequest()
                .handle(new PermissionRequest(), null).join();
        assertThat(result.getKind()).isEqualTo(PermissionRequestResult.reject("x").getKind());
        assertThat(config.getEnableSkills()).contains(false);
        assertThat(config.getSkipCustomInstructions()).contains(true);
        assertThat(config.getEnableConfigDiscovery()).contains(false);
        assertThat(config.getWorkingDirectory()).isEqualTo(EMPTY.toString());
    }

    @Test
    void outilTerminalSansPermissionQuiCaptureLAppel() {
        List<CapturedCall> captured = new ArrayList<>();
        ToolDefinition tool = tool("search_knowledge", captured);
        assertThat(tool.isTerminal()).isTrue();
        assertThat(tool.skipPermission()).isTrue();
        assertThat(tool.description()).isEqualTo("Outil search_knowledge");

        ToolInvocation invocation = new ToolInvocation().setToolCallId("toolu_1").setToolName("search_knowledge")
                .setArguments(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("query", "avoirs"));
        Object ack = tool.handler().invoke(invocation).join();
        assertThat(ack).isEqualTo(CopilotRuntime.CAPTURED);
        assertThat(captured).containsExactly(new CapturedCall("toolu_1", "search_knowledge", "{\"query\":\"avoirs\"}"));
    }

    @Test
    void dossierDeTravailPriveEtVide(@TempDir Path data) throws Exception {
        CopilotRuntime runtime = new CopilotRuntime();
        runtime.dataDir = data.toString();
        Path empty = runtime.emptyDirectory();
        assertThat(empty).isEqualTo(data.resolve("copilot").resolve("empty")).isEmptyDirectory();
        if (Files.getFileStore(empty).supportsFileAttributeView("posix")) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(empty))).isEqualTo("rwx------");
        }
    }
}
