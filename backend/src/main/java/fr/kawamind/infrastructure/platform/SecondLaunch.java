package fr.kawamind.infrastructure.platform;

import fr.kawamind.domain.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Lancement qui trouve le verrou de l'instance pris (019, research.md R5) : il demande à l'instance
 * en marche d'ouvrir un onglet, puis se termine. Sans canal, il retente pendant 15 s : l'instance
 * démarre ou s'arrête, et le verrou peut se libérer.
 */
public final class SecondLaunch {

    static final Duration PATIENCE = Duration.ofSeconds(15);
    static final Duration RETRY = Duration.ofMillis(250);
    public static final int NOT_RESPONDING = 3;

    /** Demande d'ouverture à l'instance : vide s'il n'y a pas de canal. */
    @FunctionalInterface
    interface Channel {
        Optional<ChannelProtocol.Reply> ask() throws TimeoutException;
    }

    /** {@code start} : devenir l'instance ; sinon sortir avec {@code exitCode}. */
    public record Result(boolean start, int exitCode) {

        static Result startInstance() {
            return new Result(true, 0);
        }

        static Result exit(int code) {
            return new Result(false, code);
        }
    }

    private final BooleanSupplier tryLock;
    private final Channel channel;
    private final ClockPort clock;
    private final Pause pause;
    private final LauncherMessages messages;

    /** Pause entre deux essais ; simulée en test. */
    @FunctionalInterface
    interface Pause {
        void pause(Duration duration) throws InterruptedException;
    }

    SecondLaunch(BooleanSupplier tryLock, Channel channel, ClockPort clock, Pause pause, LauncherMessages messages) {
        this.tryLock = tryLock;
        this.channel = channel;
        this.clock = clock;
        this.pause = pause;
        this.messages = messages;
    }

    public Result run() {
        Instant limit = clock.now().plus(PATIENCE);
        while (true) {
            if (tryLock.getAsBoolean()) {
                return Result.startInstance();
            }
            Optional<ChannelProtocol.Reply> reply;
            try {
                reply = channel.ask();
            } catch (TimeoutException e) {
                messages.notResponding();
                return Result.exit(NOT_RESPONDING);
            }
            if (reply.isPresent()) {
                switch (reply.get().status()) {
                    case OPENED -> {
                        messages.opened();
                        return Result.exit(0);
                    }
                    case PRINT -> {
                        messages.print(reply.get().url());
                        return Result.exit(0);
                    }
                    case UNSUPPORTED -> {
                        messages.notResponding();
                        return Result.exit(NOT_RESPONDING);
                    }
                    case STOPPING -> {
                        // l'instance s'arrête : le verrou va se libérer
                    }
                }
            }
            if (!clock.now().isBefore(limit)) {
                messages.notResponding();
                return Result.exit(NOT_RESPONDING);
            }
            try {
                pause.pause(RETRY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.exit(NOT_RESPONDING);
            }
        }
    }
}
