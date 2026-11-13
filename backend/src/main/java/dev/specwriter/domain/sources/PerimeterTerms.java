package dev.specwriter.domain.sources;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Termes d'une analyse de périmètre (013, data-model.md) : de 1 à 10, de 2 à 100 caractères,
 * cherchés littéralement sans tenir compte de la casse. Un terme retiré l'est avec sa raison.
 */
public record PerimeterTerms(List<String> values, List<Rejected> rejected) {

    public static final int MAX_TERMS = 10;
    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;

    public enum Rejection { TOO_SHORT, TOO_LONG, DUPLICATE, OVER_LIMIT }

    public record Rejected(String term, Rejection reason) {
    }

    public PerimeterTerms {
        values = List.copyOf(values);
        rejected = List.copyOf(rejected);
    }

    public static PerimeterTerms of(List<String> raw) {
        List<String> kept = new ArrayList<>();
        List<Rejected> rejected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : raw == null ? List.<String>of() : raw) {
            String term = value == null ? "" : value.strip();
            if (term.isEmpty()) {
                continue;
            }
            if (term.length() < MIN_LENGTH) {
                rejected.add(new Rejected(term, Rejection.TOO_SHORT));
            } else if (term.length() > MAX_LENGTH) {
                rejected.add(new Rejected(term, Rejection.TOO_LONG));
            } else if (!seen.add(term.toLowerCase(Locale.ROOT))) {
                rejected.add(new Rejected(term, Rejection.DUPLICATE));
            } else if (kept.size() >= MAX_TERMS) {
                rejected.add(new Rejected(term, Rejection.OVER_LIMIT));
            } else {
                kept.add(term);
            }
        }
        if (kept.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Aucun terme à chercher : chaque terme compte de 2 à 100 caractères.");
        }
        return new PerimeterTerms(kept, rejected);
    }
}
