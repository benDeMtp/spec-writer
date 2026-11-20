package fr.kawamind.domain.jira;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.util.Locale;
import java.util.Objects;

/**
 * Tri de la vue Jira (017, research.md R1) : par date de modification, par statut (catégorie puis
 * nom) ou par état de synchronisation avec la spécification liée. Les critères secondaires restent
 * croissants pour une lecture stable.
 */
public record IssueSort(Field field, Direction direction) {

    public enum Field { UPDATED, STATUS, SYNC }

    public enum Direction { ASC, DESC }

    public static final IssueSort DEFAULT = new IssueSort(Field.UPDATED, Direction.DESC);

    public IssueSort {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(direction, "direction");
    }

    /** Lit les paramètres de l'URL ; toute valeur hors liste est refusée. */
    public static IssueSort parse(String sort, String dir) {
        Field field = sort == null || sort.isBlank() ? DEFAULT.field() : switch (sort.toLowerCase(Locale.ROOT)) {
            case "updated" -> Field.UPDATED;
            case "status" -> Field.STATUS;
            case "sync" -> Field.SYNC;
            default -> throw invalid();
        };
        Direction direction = dir == null || dir.isBlank() ? Direction.DESC : switch (dir.toLowerCase(Locale.ROOT)) {
            case "asc" -> Direction.ASC;
            case "desc" -> Direction.DESC;
            default -> throw invalid();
        };
        return new IssueSort(field, direction);
    }

    /** Catégorie Jira : à faire, en cours, terminé, puis inconnue. */
    public static int statusRank(String category) {
        if (category == null) {
            return 3;
        }
        return switch (category) {
            case "new" -> 0;
            case "indeterminate" -> 1;
            case "done" -> 2;
            default -> 3;
        };
    }

    /** Du plus urgent au moins urgent : conflit, spécification plus récente, à jour, non liée. */
    public static int syncRank(ListedIssue.Sync sync) {
        return sync.ordinal();
    }

    private static DomainException invalid() {
        return new DomainException(ErrorCode.VALIDATION_FAILED,
                "Tri inconnu : colonnes updated, status ou sync, sens asc ou desc.");
    }
}
