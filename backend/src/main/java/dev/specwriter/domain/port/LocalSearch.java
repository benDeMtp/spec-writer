package dev.specwriter.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Recherche plein texte sur les sessions d'un projet et leur dernière version (FR-040, FR-005 de 002). */
public interface LocalSearch {

    record Hit(UUID sessionId, String title, String excerpt, Instant updatedAt) {
    }

    /** Spécification trouvée pour l'agent (010) : dernière version, son texte, feature implémentée ou non. */
    record SpecHit(UUID sessionId, int version, String title, String text, boolean implemented) {

        @Override
        public String toString() {
            return "SpecHit[" + sessionId + ", v" + version + "]";
        }
    }

    /** Termes saisis librement ; ils ne sont jamais interprétés comme une syntaxe de requête. */
    List<Hit> search(UUID projectId, String terms, int limit);

    /**
     * Sessions qui correspondent, de la plus récemment modifiée à la plus ancienne, à partir de
     * {@code offset} : la source locale de la liste unique paginée (FR-021a de 002).
     */
    List<Hit> byDate(UUID projectId, String terms, int offset, int limit);

    /** Nombre de sessions du projet qui correspondent. */
    int count(UUID projectId, String terms);

    /** La session correspond-elle ? Un ticket qui lui est lié n'est alors montré qu'une fois, côté local. */
    boolean matches(UUID projectId, String terms, UUID sessionId);

    /**
     * Spécifications du projet qui partagent au moins un mot avec {@code terms}, les plus pertinentes
     * d'abord ; seulement celles qui ont une version, dans sa dernière version (010, research.md R3).
     */
    List<SpecHit> byRelevance(UUID projectId, String terms, int limit);
}
