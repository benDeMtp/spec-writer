package dev.specwriter.domain.port;

import java.time.Instant;

/** Horloge injectée, pour des tests déterministes (principe III). */
public interface ClockPort {
    Instant now();
}
