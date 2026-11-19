package fr.kawamind.infrastructure.events;

import fr.kawamind.domain.sync.SourceUpdated;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Sources mises à jour, diffusées à tous les onglets abonnés (017, research.md R5) : les 50
 * dernières restent en mémoire pour un onglet qui se reconnecte avec {@code Last-Event-ID}.
 */
@ApplicationScoped
public class SourceUpdates {

    static final int BACKLOG = 50;

    private final BroadcastProcessor<SourceUpdated> live = BroadcastProcessor.create();
    private final Deque<SourceUpdated> backlog = new ArrayDeque<>();

    /** Après la validation de la transaction, s'il y en a une ; sinon aussitôt. */
    void on(@Observes(during = TransactionPhase.AFTER_SUCCESS) SourceUpdated event) {
        synchronized (backlog) {
            backlog.addLast(event);
            while (backlog.size() > BACKLOG) {
                backlog.removeFirst();
            }
        }
        live.onNext(event);
    }

    /** Événements publiés après {@code lastEventId}, encore en mémoire ; rien pour un identifiant inconnu. */
    public List<SourceUpdated> after(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return List.of();
        }
        List<SourceUpdated> out = new ArrayList<>();
        boolean found = false;
        synchronized (backlog) {
            for (SourceUpdated e : backlog) {
                if (found) {
                    out.add(e);
                } else if (e.id().toString().equals(lastEventId)) {
                    found = true;
                }
            }
        }
        return out;
    }

    public Multi<SourceUpdated> stream() {
        return live;
    }
}
