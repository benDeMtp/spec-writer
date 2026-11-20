package fr.kawamind.domain.jira;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ticket de la vue Jira, lu dans l'index local du projet (017, US1) : aucun appel à Jira pour
 * l'afficher ni le trier.
 */
public record ListedIssue(String key, String title, String status, String statusCategory, Instant updatedAt, Sync sync,
        UUID sessionId) {

    /** État de synchronisation avec la spécification liée, dans l'ordre du tri. */
    public enum Sync {
        CONFLICT,
        LOCAL_AHEAD,
        IN_SYNC,
        UNLINKED;

        public static Sync of(LinkedJiraTicket.SyncState state) {
            return state == null ? UNLINKED : valueOf(state.name());
        }
    }

    public ListedIssue {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sync, "sync");
    }
}
