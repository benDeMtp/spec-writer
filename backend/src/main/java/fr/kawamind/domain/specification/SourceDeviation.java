package fr.kawamind.domain.specification;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.SourceOrigin;
import java.time.Instant;
import java.util.Objects;

/**
 * Écart entre une source de référence et une interprétation (016, R8, FR-021) : les deux extraits
 * cités, chacun avec sa source et sa date, et ce qui diffère. Un écart n'est gardé que si ses deux
 * références sont dans la connaissance transmise ; la spécification suit la référence.
 */
public record SourceDeviation(Cited reference, Cited interpretation, String difference) {

    public static final int MAX_DIFFERENCE = 1_000;
    /** Écarts entre sources au plus par version. */
    public static final int MAX_PER_VERSION = 10;

    /** Extrait cité : sa référence, sa sorte de source, son titre et sa date (nuls si inconnus). */
    public record Cited(String ref, SourceOrigin origin, String title, Instant updatedAt) {

        public Cited {
            if (ref == null || ref.isBlank()) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Un écart entre sources cite deux extraits.");
            }
        }
    }

    public SourceDeviation {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(interpretation, "interpretation");
        difference = difference == null ? "" : difference.strip();
        if (difference.isEmpty() || difference.length() > MAX_DIFFERENCE) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Un écart entre sources dit ce qui diffère, en 1 000 caractères au plus.");
        }
    }
}
