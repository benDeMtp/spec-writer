package dev.specwriter.infrastructure.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.jira.IssueTrackerException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T063 : limiteur de débit vers Jira, horloge et attente simulées (FR-022a, research.md R13). */
class JiraThrottleTest {

    /** Horloge simulée : dormir la fait avancer. */
    long now = 1_000_000_000L;
    final List<Long> sleeps = new ArrayList<>();
    final List<Long> sentAt = new ArrayList<>();

    JiraThrottle throttle(Duration maxWait) {
        return new JiraThrottle(8, maxWait, () -> now, nanos -> {
            sleeps.add(nanos);
            now += nanos;
        });
    }

    void send(JiraThrottle t) {
        t.acquire();
        sentAt.add(now);
    }

    @Test
    void huitSansAttendrePuisLaNeuviemeAttendLaPlusAnciennePlusUneSeconde() {
        JiraThrottle t = throttle(JiraThrottle.MAX_WAIT);
        long first = now;
        for (int i = 0; i < 8; i++) {
            send(t);
            now += 10_000_000;
        }
        assertThat(sleeps).isEmpty();
        send(t);
        assertThat(sleeps).hasSize(1);
        assertThat(sentAt.getLast()).isEqualTo(first + JiraThrottle.SECOND);
    }

    @Test
    void uneRafaleDe50NeDepassePasHuitParSeconde() {
        JiraThrottle t = throttle(JiraThrottle.MAX_WAIT);
        for (int i = 0; i < 50; i++) {
            send(t);
        }
        assertThat(sentAt).hasSize(50);
        for (long start : sentAt) {
            long inWindow = sentAt.stream().filter(at -> at >= start && at < start + Duration.ofSeconds(1).toNanos()).count();
            assertThat(inWindow).isLessThanOrEqualTo(8);
        }
    }

    @Test
    void attenteDePlusDeDixSecondesEnErreurDeDebit() {
        JiraThrottle t = throttle(Duration.ofMillis(500));
        for (int i = 0; i < 8; i++) {
            send(t);
        }
        assertThatThrownBy(() -> send(t)).isInstanceOfSatisfying(IssueTrackerException.class,
                e -> assertThat(e.kind()).isEqualTo(IssueTrackerException.Kind.RATE_LIMITED));
    }

    @Test
    void interruptionPendantLAttenteJiraInjoignable() {
        JiraThrottle t = new JiraThrottle(8, JiraThrottle.MAX_WAIT, () -> now, nanos -> {
            throw new InterruptedException("arrêt");
        });
        for (int i = 0; i < 8; i++) {
            t.acquire();
        }
        try {
            assertThatThrownBy(t::acquire).isInstanceOfSatisfying(IssueTrackerException.class,
                    e -> assertThat(e.kind()).isEqualTo(IssueTrackerException.Kind.UNREACHABLE));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
