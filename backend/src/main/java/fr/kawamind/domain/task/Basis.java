package fr.kawamind.domain.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.util.Objects;

/** Base d'une tâche : nom du module ou code du cas d'usage, absent pour {@link BasisKind#NONE}. */
public record Basis(BasisKind kind, String ref) {

    public static final Basis NONE = new Basis(BasisKind.NONE, null);

    public Basis {
        Objects.requireNonNull(kind);
        ref = ref == null ? null : ref.strip();
        if ((kind == BasisKind.NONE) != (ref == null || ref.isEmpty())) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La base d'une tâche nomme un module ou un cas d'usage.");
        }
    }

    public static Basis module(String name) {
        return new Basis(BasisKind.MODULE, name);
    }

    public static Basis useCase(String code) {
        return new Basis(BasisKind.USE_CASE, code);
    }
}
