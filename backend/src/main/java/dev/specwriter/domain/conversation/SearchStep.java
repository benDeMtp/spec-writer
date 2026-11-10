package dev.specwriter.domain.conversation;

import java.util.Set;

/** Recherche lancée par l'agent pendant une réponse (010, FR-002a) : 5 au plus par réponse. */
public record SearchStep(int ordinal, String query, Set<SourceKind> kinds, int hits) {

    public static final int MAX_STEPS = 5;
    public static final int MAX_QUERY = 300;

    public SearchStep {
        if (ordinal < 1 || ordinal > MAX_STEPS) {
            throw new IllegalArgumentException("Étape hors de 1 à 5 : " + ordinal);
        }
        query = query == null ? "" : query.strip();
        query = query.length() > MAX_QUERY ? query.substring(0, MAX_QUERY) : query;
        kinds = kinds == null ? Set.of() : Set.copyOf(kinds);
        hits = Math.max(0, hits);
    }
}
