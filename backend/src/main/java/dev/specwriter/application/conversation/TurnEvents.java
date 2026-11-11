package dev.specwriter.application.conversation;

import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.Thinking;
import dev.specwriter.domain.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Événements d'un tour de l'agent, en mémoire (010, research.md R2) : un abonné reçoit d'abord tout
 * ce qui a déjà été produit, puis la suite ; un rechargement de l'écran ne perd rien. Un tour est
 * oublié 10 minutes après sa fin.
 */
public class TurnEvents {

    static final Duration KEEP = Duration.ofMinutes(10);

    /** Ce que reçoit l'écran pendant un tour. */
    public sealed interface Event {

        /** Début du tour, pour le chronomètre d'un écran qui se rattache en cours de route. */
        record Started(Instant at) implements Event {
        }

        record Step(SearchStep step) implements Event {
        }

        /** Morceau de la réponse ; le texte n'apparaît jamais dans {@link #toString()}. */
        record Delta(String text) implements Event {

            @Override
            public String toString() {
                return "Delta[" + text.length() + " caractères]";
            }
        }

        /** L'agent passe à une autre tâche : résumer sa mémoire, choisir une recherche, rédiger. */
        record Stage(Thinking.Stage stage) implements Event {
        }

        /** Morceau de la réflexion du modèle, pour la tâche en cours ; jamais dans {@link #toString()}. */
        record Reasoning(String text) implements Event {

            @Override
            public String toString() {
                return "Reasoning[" + text.length() + " caractères]";
            }
        }

        record Sources(List<CitedSource> sources) implements Event {
        }

        /** Compactage de la mémoire : commencé, fini, ou en échec (la réponse continue). */
        record Memory(ConversationHistory.Phase phase) implements Event {
        }

        /**
         * {@code durationMs} : durée du tour, de l'envoi de la question à la fin de la réponse. Pour
         * une proposition (011) : {@code proposalId} quand une version est proposée, sinon
         * {@code unchanged} et sa raison.
         */
        record Done(MessageState state, boolean historyTruncated, long durationMs, UUID proposalId, boolean unchanged,
                String reason) implements Event {

            public Done(MessageState state, boolean historyTruncated, long durationMs) {
                this(state, historyTruncated, durationMs, null, false, null);
            }
        }

        record Error(String code, String userMessage) implements Event {
        }

        default boolean last() {
            return this instanceof Done || this instanceof Error;
        }
    }

    /** Abonnement à fermer quand l'écran se déconnecte. */
    public interface Subscription extends AutoCloseable {

        boolean finished();

        @Override
        void close();
    }

    private static final class Turn {
        final List<Event> events = new ArrayList<>();
        final List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
        volatile boolean cancelled;
        Instant finishedAt;
    }

    private final Map<UUID, Turn> turns = new ConcurrentHashMap<>();
    private final ClockPort clock;

    public TurnEvents(ClockPort clock) {
        this.clock = clock;
    }

    public void open(UUID turnId) {
        purge();
        turns.put(turnId, new Turn());
    }

    public void publish(UUID turnId, Event event) {
        Turn turn = turns.get(turnId);
        if (turn == null) {
            return;
        }
        List<Consumer<Event>> listeners;
        synchronized (turn) {
            turn.events.add(event);
            if (event.last()) {
                turn.finishedAt = clock.now();
            }
            listeners = List.copyOf(turn.listeners);
        }
        listeners.forEach(l -> l.accept(event));
    }

    /** Rejoue les événements déjà produits puis transmet la suite ; vide si le tour est inconnu ou oublié. */
    public Optional<Subscription> subscribe(UUID turnId, Consumer<Event> listener) {
        Turn turn = turns.get(turnId);
        if (turn == null) {
            return Optional.empty();
        }
        List<Event> past;
        synchronized (turn) {
            past = List.copyOf(turn.events);
            turn.listeners.add(listener);
        }
        past.forEach(listener);
        return Optional.of(new Subscription() {
            @Override
            public boolean finished() {
                return turn.finishedAt != null;
            }

            @Override
            public void close() {
                turn.listeners.remove(listener);
            }
        });
    }

    public void cancel(UUID turnId) {
        Turn turn = turns.get(turnId);
        if (turn != null) {
            turn.cancelled = true;
        }
    }

    public boolean cancelled(UUID turnId) {
        Turn turn = turns.get(turnId);
        return turn != null && turn.cancelled;
    }

    private void purge() {
        Instant limit = clock.now().minus(KEEP);
        turns.values().removeIf(t -> t.finishedAt != null && t.finishedAt.isBefore(limit));
    }
}
