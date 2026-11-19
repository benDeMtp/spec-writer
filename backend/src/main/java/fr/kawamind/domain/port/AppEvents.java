package fr.kawamind.domain.port;

import fr.kawamind.domain.sync.SourceUpdated;

/** Événements de l'application vers les onglets ouverts (017, US3, research.md R5). */
@FunctionalInterface
public interface AppEvents {

    void publish(SourceUpdated event);
}
