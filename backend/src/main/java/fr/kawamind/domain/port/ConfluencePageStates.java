package fr.kawamind.domain.port;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pages lues de chaque racine Confluence (016, R3) : numéro de version et date, pour ne relire que
 * les pages qui changent. Aucun contenu.
 */
public interface ConfluencePageStates {

    record PageState(int version, Instant updatedAt) {
    }

    /** Pages de la racine, par identifiant. */
    Map<String, PageState> of(UUID sourceId);

    void replace(UUID sourceId, Map<String, PageState> pages);

    /** Racines Confluence du projet qui atteignent chaque page, par identifiant de page. */
    Map<String, Set<UUID>> roots(UUID projectId);
}
