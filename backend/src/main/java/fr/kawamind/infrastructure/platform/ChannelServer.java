package fr.kawamind.infrastructure.platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import org.jboss.logging.Logger;

/**
 * Serveur du canal sur une socket Unix (019, research.md R2) : une demande par connexion, une ligne
 * de 4 Kio au plus. Seul le compte de l'utilisateur peut s'y connecter, par les droits du dossier.
 */
final class ChannelServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ChannelServer.class);

    private final Path socket;
    private final ServerSocketChannel server;
    private final UnaryOperator<String> handler;

    private ChannelServer(Path socket, ServerSocketChannel server, UnaryOperator<String> handler) {
        this.socket = socket;
        this.server = server;
        this.handler = handler;
    }

    /** Ouvre le canal ; une socket orpheline d'un arrêt brutal est remplacée. */
    static ChannelServer open(Path socket, UnaryOperator<String> handler) throws IOException {
        Files.deleteIfExists(socket);
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            server.bind(UnixDomainSocketAddress.of(socket));
        } catch (IOException | RuntimeException e) {
            server.close();
            throw e;
        }
        ChannelServer channel = new ChannelServer(socket, server, handler);
        Thread.ofVirtual().name("spec-writer-instance-channel").start(channel::accept);
        return channel;
    }

    private void accept() {
        while (server.isOpen()) {
            try {
                SocketChannel client = server.accept();
                Thread.ofVirtual().start(() -> serve(client));
            } catch (ClosedChannelException e) {
                return;
            } catch (IOException e) {
                LOG.debugf("Canal de l'instance : connexion refusée (%s)", e.getClass().getSimpleName());
            }
        }
    }

    private void serve(SocketChannel client) {
        try (client) {
            String line = readLine(client);
            if (line == null) {
                return;
            }
            client.write(ByteBuffer.wrap((handler.apply(line) + "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            LOG.debugf("Canal de l'instance : échange interrompu (%s)", e.getClass().getSimpleName());
        }
    }

    /** Lit une ligne ; {@code null} au-delà de 4 Kio ou si la connexion se ferme avant la fin de ligne. */
    static String readLine(SocketChannel channel) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(512);
        while (channel.read(buffer) >= 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                byte b = buffer.get();
                if (b == '\n') {
                    return line.toString(StandardCharsets.UTF_8);
                }
                if (line.size() >= ChannelProtocol.MAX_LINE) {
                    return null;
                }
                line.write(b);
            }
            buffer.clear();
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        server.close();
        Files.deleteIfExists(socket);
    }
}
