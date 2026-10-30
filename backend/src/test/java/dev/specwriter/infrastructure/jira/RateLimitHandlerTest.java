package dev.specwriter.infrastructure.jira;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitHandlerTest {

    static final Instant NOW = Instant.parse("2026-10-30T09:00:00Z");

    @Test
    void retryAfterEnSecondesOuEnDate() {
        assertThat(RateLimitHandler.delay(Map.of("Retry-After", List.of("2")), NOW)).isEqualTo(Duration.ofSeconds(2));
        assertThat(RateLimitHandler.delay(Map.of("retry-after", List.of("Fri, 30 Oct 2026 09:00:05 GMT")), NOW))
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void plafondA30Secondes() {
        assertThat(RateLimitHandler.delay(Map.of("RETRY-AFTER", List.of("120")), NOW)).isEqualTo(Duration.ofSeconds(30));
        assertThat(RateLimitHandler.delay(Map.of("Retry-After", List.of("-3")), NOW)).isEqualTo(Duration.ZERO);
    }

    @Test
    void enTetesDeLimitationSansCasse() {
        assertThat(RateLimitHandler.delay(Map.of("x-ratelimit-interval-seconds", List.of("4")), NOW))
                .isEqualTo(Duration.ofSeconds(4));
        assertThat(RateLimitHandler.delay(Map.of("X-RateLimiting-Interval-Seconds", List.of("7")), NOW))
                .isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void valeurAbsenteOuIllisible() {
        assertThat(RateLimitHandler.delay(Map.of(), NOW)).isEqualTo(RateLimitHandler.DEFAULT_DELAY);
        assertThat(RateLimitHandler.delay(Map.of("Retry-After", List.of("bientôt")), NOW)).isEqualTo(RateLimitHandler.DEFAULT_DELAY);
    }
}
