package fr.kawamind.support;

import fr.kawamind.domain.port.ClockPort;
import java.time.Duration;
import java.time.Instant;

/** Horloge de test, avançable à la main. */
public class FixedClock implements ClockPort {

    private Instant now;

    public FixedClock(Instant start) {
        this.now = start;
    }

    public FixedClock() {
        this(Instant.parse("2026-10-30T09:00:00Z"));
    }

    @Override
    public Instant now() {
        return now;
    }

    public void advance(Duration d) {
        now = now.plus(d);
    }
}
