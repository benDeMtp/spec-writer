package dev.specwriter.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Recherche plein texte sur les sessions et leur dernière version (FR-040). */
public interface LocalSearch {

    record Hit(UUID sessionId, String title, String excerpt, Instant updatedAt) {
    }

    /** Termes saisis librement ; ils ne sont jamais interprétés comme une syntaxe de requête. */
    List<Hit> search(String terms, int limit);
}
