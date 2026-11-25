package fr.kawamind.application.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Travaux synchrones en cours, des générations surtout : un appel au modèle qui dure, sans tour à
 * suivre (019, research.md R4). Ils ne s'annulent pas ; ils sont seulement nommés avant Quitter.
 */
public class LongWorkTracker {

    /** Jeton d'un travail commencé, à rendre à la fin. */
    public record Ticket(long id) {
    }

    public record Entry(WorkKind kind, UUID projectId) {
    }

    private final Map<Long, Entry> current = new ConcurrentHashMap<>();
    private final AtomicLong next = new AtomicLong();

    public Ticket begin(WorkKind kind, UUID projectId) {
        long id = next.incrementAndGet();
        current.put(id, new Entry(kind, projectId));
        return new Ticket(id);
    }

    public void end(Ticket ticket) {
        current.remove(ticket.id());
    }

    public List<Entry> current() {
        return List.copyOf(current.values());
    }
}
