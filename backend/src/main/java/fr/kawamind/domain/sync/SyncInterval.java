package fr.kawamind.domain.sync;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Duration;
import java.util.Objects;

/** Délai de synchronisation automatique d'une source, en liste fermée (017, FR-007). */
public enum SyncInterval {
    OFF(null),
    M15(15),
    M30(30),
    H1(60),
    H4(240),
    D1(1440);

    private final Integer minutes;

    SyncInterval(Integer minutes) {
        this.minutes = minutes;
    }

    public Integer minutes() {
        return minutes;
    }

    public static SyncInterval fromMinutes(Integer minutes) {
        for (SyncInterval i : values()) {
            if (Objects.equals(i.minutes, minutes)) {
                return i;
            }
        }
        throw new DomainException(ErrorCode.VALIDATION_FAILED,
                "Délai inconnu : désactivé, 15, 30, 60, 240 ou 1440 minutes.");
    }

    /** Durée du délai ; {@code minute} vaut une minute, sauf pour accélérer les E2E. */
    public Duration duration(Duration minute) {
        if (minutes == null) {
            throw new IllegalStateException("délai désactivé");
        }
        return minute.multipliedBy(minutes);
    }
}
