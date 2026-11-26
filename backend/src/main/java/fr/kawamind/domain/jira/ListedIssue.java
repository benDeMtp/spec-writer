package fr.kawamind.domain.jira;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ticket de la vue Jira, lu dans l'index local du projet (017, US1) : aucun appel à Jira pour
 * l'afficher ni le trier. Un ticket du premier niveau porte ses sous-tâches indexées (022).
 *
 * @param parentKey parent d'une sous-tâche du premier niveau dont le parent n'est pas indexé
 *                  (022, FR-005) ; nul sinon
 * @param subtasks  sous-tâches indexées, classées par clé dans l'ordre numérique ; vides pour une
 *                  sous-tâche (un seul niveau)
 */
public record ListedIssue(String key, String title, String status, String statusCategory, Instant updatedAt, Sync sync,
        UUID sessionId, String parentKey, List<ListedIssue> subtasks) {

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
        subtasks = subtasks == null ? List.of() : List.copyOf(subtasks);
    }

    public ListedIssue(String key, String title, String status, String statusCategory, Instant updatedAt, Sync sync,
            UUID sessionId) {
        this(key, title, status, statusCategory, updatedAt, sync, sessionId, null, List.of());
    }

    /** Le même ticket, avec ses sous-tâches. */
    public ListedIssue withSubtasks(List<ListedIssue> children) {
        return new ListedIssue(key, title, status, statusCategory, updatedAt, sync, sessionId, parentKey, children);
    }

    /** Ordre des clés Jira : préfixe, puis numéro (AURORA-9 avant AURORA-10). */
    public static final java.util.Comparator<String> KEY_ORDER = java.util.Comparator
            .comparing((String k) -> k.contains("-") ? k.substring(0, k.lastIndexOf('-')) : k)
            .thenComparingLong(ListedIssue::number).thenComparing(java.util.Comparator.naturalOrder());

    private static long number(String key) {
        String tail = key.substring(key.lastIndexOf('-') + 1);
        return tail.chars().allMatch(Character::isDigit) && !tail.isEmpty() && tail.length() < 18
                ? Long.parseLong(tail) : Long.MAX_VALUE;
    }
}
