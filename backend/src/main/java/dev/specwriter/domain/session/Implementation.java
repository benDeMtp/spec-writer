package dev.specwriter.domain.session;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Avancement de l'implémentation d'une spécification (FR-026 à FR-027). Rangé à part de la
 * {@link Session} : seul le changement de statut l'écrit, si bien qu'un enregistrement de la
 * session pendant une opération de l'IA ne peut pas annuler un passage à « implémentée ».
 *
 * @param implementedAt date du passage à {@code IMPLEMENTED}, effacée en le quittant
 * @param evolvedFrom   spécification d'origine d'une évolution (FR-026b)
 */
public record Implementation(ImplementationStatus status, Instant implementedAt, UUID evolvedFrom) {

    public Implementation {
        Objects.requireNonNull(status);
    }

    public static Implementation initial() {
        return new Implementation(ImplementationStatus.DRAFT, null, null);
    }

    /** Évolution d'une spécification : un brouillon qui cite son origine. */
    public static Implementation evolvedFrom(UUID origin) {
        return new Implementation(ImplementationStatus.DRAFT, null, origin);
    }

    /**
     * Nouveau statut. Quitter {@code IMPLEMENTED} demande la confirmation de l'utilisateur, qui
     * rappelle la date d'implémentation ; un agent n'a que ses deux transitions.
     */
    public Implementation change(ImplementationStatus target, ImplementationStatus.Actor actor, boolean confirmUnlock,
            Instant now) {
        if (target == status) {
            return this;
        }
        if (!status.allows(target, actor)) {
            throw new DomainException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        if (status == ImplementationStatus.IMPLEMENTED && !confirmUnlock) {
            throw new DomainException(ErrorCode.UNLOCK_CONFIRMATION_REQUIRED);
        }
        return new Implementation(target, target == ImplementationStatus.IMPLEMENTED ? now : null, evolvedFrom);
    }
}
