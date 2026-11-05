package dev.specwriter.infrastructure.logging;

import java.time.Instant;
import java.util.List;

/** Entrée du journal des erreurs (FR-031, research.md R15), déjà expurgée. */
public record JournalEntry(Instant at, String level, String logger, String message, String cause, List<String> stack) {

    public JournalEntry {
        stack = stack == null ? List.of() : List.copyOf(stack);
    }
}
