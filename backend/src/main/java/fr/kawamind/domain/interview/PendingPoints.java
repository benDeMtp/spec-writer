package fr.kawamind.domain.interview;

import fr.kawamind.domain.specification.SpecificationContent;
import java.util.Set;

/**
 * Points à traiter d'une spécification (021, FR-025) : ceux de sa dernière version, par sorte, moins
 * ceux qu'une séance terminée a tranchés et dont la révision n'est pas encore appliquée.
 */
public record PendingPoints(int assumptions, int openQuestions, int sourceDeviations) {

    public static PendingPoints of(SpecificationContent latest, Set<String> decided) {
        return new PendingPoints(
                (int) latest.assumptions().stream().filter(a -> !decided.contains(a.code())).count(),
                (int) latest.openQuestions().stream().filter(q -> !decided.contains(q.code())).count(),
                (int) latest.sourceDeviations().stream().filter(d -> !decided.contains(d.code())).count());
    }

    public int total() {
        return assumptions + openQuestions + sourceDeviations;
    }
}
