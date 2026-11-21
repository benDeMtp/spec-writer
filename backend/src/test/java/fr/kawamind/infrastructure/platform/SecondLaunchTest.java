package fr.kawamind.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.support.FixedClock;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/** T031 : second lancement, attentes, messages et codes de sortie (019, R5). */
class SecondLaunchTest {

    final FixedClock clock = new FixedClock();
    final ByteArrayOutputStream console = new ByteArrayOutputStream();
    final LauncherMessages messages = new LauncherMessages(new PrintStream(console, true, StandardCharsets.UTF_8),
            "Mac OS X", c -> false, (t, x) -> { });
    final Deque<Object> replies = new ArrayDeque<>();
    Instant lockFreeAt;

    SecondLaunch.Result run() {
        SecondLaunch.Channel channel = () -> {
            Object next = replies.isEmpty() ? Optional.empty() : replies.poll();
            if (next instanceof TimeoutException e) {
                throw e;
            }
            @SuppressWarnings("unchecked")
            Optional<ChannelProtocol.Reply> reply = (Optional<ChannelProtocol.Reply>) next;
            return reply;
        };
        return new SecondLaunch(() -> lockFreeAt != null && !clock.now().isBefore(lockFreeAt), channel, clock,
                clock::advance, messages).run();
    }

    static Optional<ChannelProtocol.Reply> reply(ChannelProtocol.Status status, String url) {
        return Optional.of(new ChannelProtocol.Reply(status, url));
    }

    String out() {
        return console.toString(StandardCharsets.UTF_8);
    }

    @Test
    void ongletOuvertCodeZero() {
        replies.add(reply(ChannelProtocol.Status.OPENED, null));

        assertThat(run()).isEqualTo(new SecondLaunch.Result(false, 0));
        assertThat(out()).contains("spec-writer est déjà lancé : un nouvel onglet a été ouvert.");
    }

    @Test
    void adresseAfficheeCodeZero() {
        replies.add(reply(ChannelProtocol.Status.PRINT, "http://127.0.0.1:47615/?launch=abc"));

        assertThat(run()).isEqualTo(new SecondLaunch.Result(false, 0));
        assertThat(out()).contains("http://127.0.0.1:47615/?launch=abc");
    }

    @Test
    void sansCanalPuisVerrouLibreIlDevientLInstance() {
        lockFreeAt = clock.now().plusSeconds(3);

        assertThat(run().start()).isTrue();
        assertThat(out()).isEmpty();
    }

    @Test
    void sansCanalPendant15SecondesIlAbandonne() {
        Instant begin = clock.now();

        assertThat(run()).isEqualTo(new SecondLaunch.Result(false, SecondLaunch.NOT_RESPONDING));
        assertThat(Duration.between(begin, clock.now())).isBetween(Duration.ofSeconds(15), Duration.ofMillis(15_250));
        assertThat(out()).contains("ne répond pas").contains("Moniteur d'activité");
    }

    @Test
    void pasDeReponseEn5SecondesCode3() {
        replies.add(new TimeoutException());

        assertThat(run().exitCode()).isEqualTo(SecondLaunch.NOT_RESPONDING);
        assertThat(out()).contains("ne répond pas");
    }

    @Test
    void instanceQuiSArreteOnRetenteJusquAuVerrou() {
        replies.add(reply(ChannelProtocol.Status.STOPPING, null));
        replies.add(reply(ChannelProtocol.Status.STOPPING, null));
        lockFreeAt = clock.now().plusMillis(600);

        assertThat(run().start()).isTrue();
    }

    @Test
    void versionInconnueCode3() {
        replies.add(reply(ChannelProtocol.Status.UNSUPPORTED, null));

        assertThat(run().exitCode()).isEqualTo(SecondLaunch.NOT_RESPONDING);
    }
}
