package fr.kawamind.domain.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Labels des tickets d'un projet (024, research R2) : remplacés à chaque lecture d'un ticket dans
 * Jira, ajoutés quand spec-writer pose le label.
 */
public interface TicketLabels {

    /** Remplace les labels d'un ticket par ceux lus dans Jira. */
    void replace(UUID projectId, String key, Collection<String> labels);

    /** Ajoute un label à un ticket ; sans effet s'il y est déjà. */
    void add(UUID projectId, String key, String label);

    boolean has(UUID projectId, String key, String label);

    /** Retire les labels des tickets qui ne sont plus dans le projet. */
    void retain(UUID projectId, Set<String> keep);

    /** Retire les labels de ces tickets. */
    void remove(UUID projectId, Collection<String> keys);
}
