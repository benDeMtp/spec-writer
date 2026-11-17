package fr.kawamind.domain.knowledge;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Provenance d'un extrait transmis à l'IA (016, research.md R5), calculée à la lecture : sa sorte
 * de source, sa date de dernière mise à jour à la source ({@code null} : « date inconnue », jamais
 * la date d'indexation, FR-012) et son niveau de fiabilité, nul pour la fiche générale et les
 * documents joints seulement ; omis pour une autre sorte, il vaut interprétation.
 */
public record Provenance(SourceOrigin origin, Instant updatedAt, Reliability level) {

    /** Fuseau des dates transmises au modèle et affichées : celui de l'utilisateur. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    public static final String UNKNOWN_DATE = "inconnue";

    public Provenance {
        Objects.requireNonNull(origin, "origin");
        if (level != null && !origin.adjustable()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "La fiche générale et les documents joints n'ont pas de niveau de fiabilité.");
        }
        if (level == null && origin.adjustable()) {
            level = Reliability.INTERPRETATION;
        }
    }

    /** Niveau par défaut de la sorte : interprétation (le résolveur applique ensuite les réglages). */
    public static Provenance of(SourceOrigin origin, Instant updatedAt) {
        return new Provenance(origin, updatedAt, null);
    }

    public Provenance withLevel(Reliability newLevel) {
        return new Provenance(origin, updatedAt, origin.adjustable() ? newLevel : null);
    }

    /** Date {@code AAAA-MM-JJ} transmise au modèle, ou {@value #UNKNOWN_DATE}. */
    public String dateTag() {
        return updatedAt == null ? UNKNOWN_DATE : updatedAt.atZone(ZONE).toLocalDate().toString();
    }
}
