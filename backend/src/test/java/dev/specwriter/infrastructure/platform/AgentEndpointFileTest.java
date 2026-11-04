package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** T049 : fichier de la passerelle MCP, {port, secret} lisible du seul utilisateur (research.md R2). */
class AgentEndpointFileTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path dir;
    final List<String> logs = new ArrayList<>();
    final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord r) {
            logs.add(String.valueOf(r.getMessage()) + " " + java.util.Arrays.toString(r.getParameters()));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void captureLogs() {
        Logger.getLogger("").addHandler(capture);
    }

    @AfterEach
    void release() {
        Logger.getLogger("").removeHandler(capture);
    }

    AgentEndpointFile endpoint() {
        return new AgentEndpointFile(dir, () -> 47615, true);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> content() throws Exception {
        return JSON.readValue(Files.readString(dir.resolve(AgentEndpointFile.FILE)), Map.class);
    }

    @Test
    void fichierPriveAvecPortEtSecretDe256Bits() throws Exception {
        AgentEndpointFile endpoint = endpoint();
        endpoint.publish();
        Path file = dir.resolve(AgentEndpointFile.FILE);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
        Map<String, Object> json = content();
        assertThat(json).containsOnlyKeys("port", "secret").containsEntry("port", 47615);
        String secret = (String) json.get("secret");
        assertThat(Base64.getUrlDecoder().decode(secret)).hasSize(32);
        assertThat(endpoint.accepts(secret)).isTrue();
        assertThat(endpoint.accepts(secret + "x")).isFalse();
        assertThat(endpoint.accepts(null)).isFalse();
        assertThat(String.join("\n", logs)).doesNotContain(secret);
    }

    @Test
    void persistantRegenereEtSupprime() throws Exception {
        AgentEndpointFile first = endpoint();
        first.publish();
        String secret = (String) content().get("secret");

        // relancement : le secret reste celui du fichier
        AgentEndpointFile restarted = endpoint();
        restarted.publish();
        assertThat(content().get("secret")).isEqualTo(secret);
        assertThat(restarted.accepts(secret)).isTrue();

        restarted.regenerate();
        String renewed = (String) content().get("secret");
        assertThat(renewed).isNotEqualTo(secret);
        assertThat(restarted.accepts(secret)).isFalse();
        assertThat(restarted.accepts(renewed)).isTrue();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve(AgentEndpointFile.FILE))))
                .isEqualTo("rw-------");

        restarted.withdraw();
        assertThat(dir.resolve(AgentEndpointFile.FILE)).doesNotExist();
        assertThat(restarted.accepts(renewed)).isFalse();
        assertThat(String.join("\n", logs)).doesNotContain(secret).doesNotContain(renewed);
        try (var files = Files.list(dir)) {
            assertThat(files).as("aucun fichier temporaire laissé").isEmpty();
        }
    }

    @Test
    void sansPublicationAucunSecretAccepte() {
        assertThat(endpoint().accepts("")).isFalse();
    }
}
