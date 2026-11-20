package fr.kawamind.infrastructure.platform;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Client du canal, pour un second lancement (019, contracts/instance-socket.md). */
final class ChannelClient implements SecondLaunch.Channel {

    private final Path socket;
    private final Duration timeout;

    ChannelClient(Path socket, Duration timeout) {
        this.socket = socket;
        this.timeout = timeout;
    }

    @Override
    public Optional<ChannelProtocol.Reply> ask() throws TimeoutException {
        if (!Files.exists(socket)) {
            return Optional.empty();
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Optional<ChannelProtocol.Reply>> exchange = executor.submit(this::exchange);
            try {
                return exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                // socket orpheline ou instance qui s'arrête : pas de canal
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } finally {
                exchange.cancel(true);
                executor.shutdownNow();
            }
        }
    }

    private Optional<ChannelProtocol.Reply> exchange() throws IOException {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));
            channel.write(ByteBuffer.wrap((ChannelProtocol.openRequest() + "\n").getBytes(StandardCharsets.UTF_8)));
            String line = ChannelServer.readLine(channel);
            return Optional.of(line == null ? new ChannelProtocol.Reply(ChannelProtocol.Status.UNSUPPORTED, null)
                    : ChannelProtocol.parseReply(line));
        }
    }
}
