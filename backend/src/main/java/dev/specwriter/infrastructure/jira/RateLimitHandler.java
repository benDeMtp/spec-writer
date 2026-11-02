package dev.specwriter.infrastructure.jira;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Délai d'attente après un 429 de Jira (research.md R10). {@code Retry-After} (secondes ou date
 * HTTP) prime, puis {@code X-RateLimit-Interval-Seconds} ou {@code X-RateLimiting-Interval-Seconds}.
 * Les noms d'en-tête sont lus sans tenir compte de la casse, et le délai est plafonné à 30 s.
 */
final class RateLimitHandler {

    static final int MAX_ATTEMPTS = 3;
    static final Duration MAX_DELAY = Duration.ofSeconds(30);
    static final Duration DEFAULT_DELAY = Duration.ofSeconds(1);

    private RateLimitHandler() {
    }

    static Duration delay(Map<String, ? extends List<?>> headers, Instant now) {
        Map<String, List<?>> ci = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ci.putAll(headers);
        Duration d = parse(first(ci, "Retry-After"), now);
        if (d == null) {
            d = parse(first(ci, "X-RateLimit-Interval-Seconds"), now);
        }
        if (d == null) {
            d = parse(first(ci, "X-RateLimiting-Interval-Seconds"), now);
        }
        if (d == null) {
            return DEFAULT_DELAY;
        }
        if (d.isNegative()) {
            return Duration.ZERO;
        }
        return d.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : d;
    }

    private static String first(Map<String, List<?>> headers, String name) {
        List<?> values = headers.get(name);
        return values == null || values.isEmpty() || values.get(0) == null ? null : values.get(0).toString().trim();
    }

    private static Duration parse(String value, Instant now) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException notSeconds) {
            try {
                return Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
    }
}
