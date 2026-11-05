package dev.specwriter.domain.port;

import dev.specwriter.domain.jira.TicketLink;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Liens des tickets indexés d'un projet (FR-044, research.md R29). */
public interface TicketLinkRepository {

    /** Lien entrant : le ticket {@code sourceKey} porte {@code link} vers le ticket demandé. */
    record Inbound(String sourceKey, TicketLink link) {
    }

    /** Remplace en bloc les liens portés par un ticket. */
    void replace(UUID projectId, String sourceKey, List<TicketLink> links);

    /** Retire les liens des tickets qui ne sont plus dans le projet ; renvoie le nombre de tickets concernés. */
    int retain(UUID projectId, Set<String> keep);

    /** Liens portés par un ticket. */
    List<TicketLink> of(UUID projectId, String sourceKey);

    /** Liens d'autres tickets qui visent ce ticket. */
    List<Inbound> pointingTo(UUID projectId, String targetKey);
}
