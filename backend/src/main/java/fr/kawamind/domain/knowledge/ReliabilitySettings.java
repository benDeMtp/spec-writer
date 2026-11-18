package fr.kawamind.domain.knowledge;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Niveaux de fiabilité des sortes de source d'un projet (016, R6) : seuls ceux qui diffèrent du
 * défaut sont gardés. Défaut : les documents SharePoint sont la référence (ils portent les demandes
 * métier), tout le reste est une interprétation (FR-016).
 */
public record ReliabilitySettings(UUID projectId, Map<SourceOrigin, Reliability> overrides) {

    public ReliabilitySettings {
        EnumMap<SourceOrigin, Reliability> kept = new EnumMap<>(SourceOrigin.class);
        if (overrides != null) {
            overrides.forEach((origin, level) -> {
                if (!origin.adjustable()) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "La fiche générale et les documents joints n'ont pas de niveau de fiabilité.");
                }
                if (level != null && level != defaultLevel(origin)) {
                    kept.put(origin, level);
                }
            });
        }
        overrides = Collections.unmodifiableMap(kept);
    }

    public static ReliabilitySettings defaults(UUID projectId) {
        return new ReliabilitySettings(projectId, Map.of());
    }

    /** Niveau par défaut d'une sorte ; {@code null} pour la fiche générale et les documents joints. */
    public static Reliability defaultLevel(SourceOrigin origin) {
        if (!origin.adjustable()) {
            return null;
        }
        return origin == SourceOrigin.DOCUMENTS ? Reliability.REFERENCE : Reliability.INTERPRETATION;
    }

    public Reliability level(SourceOrigin origin) {
        return overrides.getOrDefault(origin, defaultLevel(origin));
    }

    /** Un niveau égal au défaut efface le réglage ; {@code null} aussi. */
    public ReliabilitySettings with(SourceOrigin origin, Reliability level) {
        EnumMap<SourceOrigin, Reliability> next = new EnumMap<>(SourceOrigin.class);
        next.putAll(overrides);
        if (level == null) {
            next.remove(origin);
        } else {
            next.put(origin, level);
        }
        return new ReliabilitySettings(projectId, next);
    }

    /** Vrai si toutes les sortes réglables ont le même niveau : aucune ne prime (FR-020). */
    public boolean uniform() {
        return Arrays.stream(SourceOrigin.values()).filter(SourceOrigin::adjustable).map(this::level).distinct()
                .count() == 1;
    }

    /**
     * Niveau d'une page atteinte par une racine Confluence : le niveau propre de la racine, sinon
     * celui de Confluence ; atteinte par plusieurs racines, le plus haut (FR-016a).
     */
    public Reliability confluence(Iterable<Reliability> rootLevels) {
        Reliability best = null;
        for (Reliability own : rootLevels) {
            best = Reliability.max(best, own == null ? level(SourceOrigin.CONFLUENCE) : own);
        }
        return best == null ? level(SourceOrigin.CONFLUENCE) : best;
    }
}
