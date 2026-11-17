package fr.kawamind.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 014 (FR-019) : la commande affichée dans « Accès des agents » lance la passerelle sous son nouveau nom. */
class McpCommandTest {

    @Test
    void laCommandeLanceLaPasserelleKawamind() throws Exception {
        McpCommand.Command command = McpCommand.current();

        assertThat(McpCommand.MAIN).isEqualTo("fr.kawamind.mcp.McpBridge");
        assertThat(Class.forName(McpCommand.MAIN)).isNotNull();
        assertThat(command.args()).last().isEqualTo(McpCommand.MAIN);
        assertThat(command.args()).contains("-cp");
        assertThat(command.line()).contains("fr.kawamind.mcp.McpBridge").doesNotContain("dev.specwriter");
        assertThat(command.mcpJson()).contains("\"spec-writer\"").contains("fr.kawamind.mcp.McpBridge");
    }
}
