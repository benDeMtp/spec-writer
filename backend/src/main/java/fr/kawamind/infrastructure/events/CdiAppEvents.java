package fr.kawamind.infrastructure.events;

import fr.kawamind.domain.port.AppEvents;
import fr.kawamind.domain.sync.SourceUpdated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/** Événements de l'application en événements CDI (017, research.md R5) ; {@link SourceUpdates} les diffuse. */
@ApplicationScoped
public class CdiAppEvents implements AppEvents {

    @Inject
    Event<SourceUpdated> sourceUpdated;

    @Override
    public void publish(SourceUpdated event) {
        sourceUpdated.fire(event);
    }
}
