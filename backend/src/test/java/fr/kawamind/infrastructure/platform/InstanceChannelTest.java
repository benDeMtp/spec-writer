package fr.kawamind.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T030 : canal de l'instance, contracts/instance-socket.md (019, R2). */
class InstanceChannelTest {

    @TempDir
    Path data;

    Path socket;
    ChannelServer server;
    final AtomicInteger calls = new AtomicInteger();
    String answer = ChannelProtocol.reply(ChannelProtocol.Status.OPENED, null);

    @BeforeEach
    void open() throws IOException {
        socket = data.resolve(ChannelProtocol.SOCKET);
        server = ChannelServer.open(socket, line -> {
            calls.incrementAndGet();
            return ChannelProtocol.isOpenRequest(line) ? answer : ChannelProtocol.reply(ChannelProtocol.Status.UNSUPPORTED, null);
        });
    }

    @AfterEach
    void close() throws IOException {
        server.close();
    }

    ChannelClient client() {
        return new ChannelClient(socket, Duration.ofSeconds(5));
    }

    String raw(String line) throws IOException {
        try (SocketChannel c = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            c.connect(UnixDomainSocketAddress.of(socket));
            c.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
            return ChannelServer.readLine(c);
        }
    }

    @Test
    void ouvertureRendueAuSecondLancement() throws Exception {
        assertThat(client().ask()).get().extracting(ChannelProtocol.Reply::status).isEqualTo(ChannelProtocol.Status.OPENED);
    }

    @Test
    void adresseRenduePourLaConsole() throws Exception {
        answer = ChannelProtocol.reply(ChannelProtocol.Status.PRINT, "http://127.0.0.1:47615/?launch=x");

        assertThat(client().ask()).contains(new ChannelProtocol.Reply(ChannelProtocol.Status.PRINT,
                "http://127.0.0.1:47615/?launch=x"));
    }

    @Test
    void versionOuCommandeInconnueRefusees() throws IOException {
        assertThat(raw("{\"version\":2,\"command\":\"open\"}\n")).contains("UNSUPPORTED");
        assertThat(raw("{\"version\":1,\"command\":\"stop\"}\n")).contains("UNSUPPORTED");
        assertThat(raw("pas du json\n")).contains("UNSUPPORTED");
    }

    @Test
    void uneLigneDePlusDe4KioFermeLaConnexion() throws IOException {
        assertThat(raw("x".repeat(5000) + "\n")).isNull();
        assertThat(calls).hasValue(0);
    }

    @Test
    void uneSeuleDemandeParConnexion() throws IOException {
        assertThat(raw(ChannelProtocol.openRequest() + "\n" + ChannelProtocol.openRequest() + "\n")).contains("OPENED");
        assertThat(calls).hasValue(1);
    }

    @Test
    void uneSocketOrphelineEstRemplacee() throws Exception {
        server.close();
        Files.writeString(socket, "orpheline");

        server = ChannelServer.open(socket, line -> answer);

        assertThat(client().ask()).isPresent();
    }

    @Test
    void aLaFermetureLeFichierDisparaitEtLeClientNeTrouvePlusDeCanal() throws Exception {
        server.close();

        assertThat(Files.exists(socket)).isFalse();
        assertThat(client().ask()).isEmpty();
    }
}
