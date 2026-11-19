package fr.kawamind.domain.sync;

import java.time.Instant;
import java.util.Objects;

/** Bilan de la dernière synchronisation d'une source : décomptes, ou code d'erreur sans contenu. */
public record SourceSyncResult(Instant at, Outcome outcome, SourceChange change, String errorCode) {

    public enum Outcome { CHANGED, UNCHANGED, ERROR }

    public SourceSyncResult {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(change, "change");
        if (outcome == Outcome.ERROR) {
            Objects.requireNonNull(errorCode, "errorCode");
        }
    }

    public static SourceSyncResult success(Instant at, SourceChange change) {
        return new SourceSyncResult(at, change.changed() ? Outcome.CHANGED : Outcome.UNCHANGED, change, null);
    }

    public static SourceSyncResult failure(Instant at, String errorCode) {
        return new SourceSyncResult(at, Outcome.ERROR, SourceChange.NONE, Objects.requireNonNull(errorCode, "errorCode"));
    }

    public boolean succeeded() {
        return outcome != Outcome.ERROR;
    }
}
