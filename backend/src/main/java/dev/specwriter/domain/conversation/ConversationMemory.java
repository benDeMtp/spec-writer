package dev.specwriter.domain.conversation;

import java.time.Instant;
import java.util.Objects;

/**
 * Mémoire d'une conversation (010) : résumé des échanges anciens, qui couvre les messages jusqu'au
 * numéro d'ordre {@code coveredUntil}. Transmis à l'agent avant les échanges récents, qui suivent
 * en entier. Le texte n'apparaît jamais dans {@link #toString()}.
 */
public record ConversationMemory(String summary, int coveredUntil, Instant updatedAt) {

    public static final int MAX_SUMMARY = 4_000;

    public ConversationMemory {
        summary = summary == null ? "" : summary.strip();
        if (summary.length() > MAX_SUMMARY) {
            summary = summary.substring(0, MAX_SUMMARY - 1).strip() + "…";
        }
        if (coveredUntil < 0) {
            throw new IllegalArgumentException("coveredUntil");
        }
        Objects.requireNonNull(updatedAt);
    }

    @Override
    public String toString() {
        return "ConversationMemory[" + summary.length() + " caractères, jusqu'au message " + coveredUntil + "]";
    }
}
