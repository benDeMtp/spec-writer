package fr.kawamind.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.conversation.TurnEvents.Event;
import fr.kawamind.domain.conversation.MessageState;
import fr.kawamind.domain.conversation.SearchStep;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.support.FixedClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 010, research.md R2 : un abonné tardif reçoit tout ce qui a déjà été produit, puis la suite. */
class TurnEventsTest {

    final FixedClock clock = new FixedClock();
    final TurnEvents events = new TurnEvents(clock);
    final UUID turn = UUID.randomUUID();

    @Test
    void rejeuPuisFluxPuisFin() {
        events.open(turn, TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());
        events.publish(turn, new Event.Step(new SearchStep(1, "avoirs", Set.of(SourceKind.TICKET), 3)));
        events.publish(turn, new Event.Delta("Les avoirs "));
        List<Event> seen = new ArrayList<>();
        try (TurnEvents.Subscription s = events.subscribe(turn, seen::add).orElseThrow()) {
            events.publish(turn, new Event.Delta("passent par le bus."));
            events.publish(turn, new Event.Done(MessageState.COMPLETE, false, 0));
            assertThat(s.finished()).isTrue();
        }
        assertThat(seen).hasSize(4);
        assertThat(seen.getFirst()).isInstanceOf(Event.Step.class);
        assertThat(seen.getLast()).isEqualTo(new Event.Done(MessageState.COMPLETE, false, 0));
        // un abonné après la fin reçoit tout, et sait que c'est fini
        List<Event> late = new ArrayList<>();
        assertThat(events.subscribe(turn, late::add).orElseThrow().finished()).isTrue();
        assertThat(late).hasSize(4);
        assertThat(events.subscribe(UUID.randomUUID(), late::add)).isEmpty();
    }

    /** Un écran dont le flux casse (onglet fermé, réponse déjà terminée) ne casse pas le tour. */
    @Test
    void abonneEnPanneNeCassePasLeTour() {
        events.open(turn, TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());
        List<Event> broken = new ArrayList<>();
        events.subscribe(turn, e -> {
            broken.add(e);
            throw new IllegalStateException("Response has already been written");
        });
        List<Event> healthy = new ArrayList<>();
        events.subscribe(turn, healthy::add);

        events.publish(turn, new Event.Delta("Les avoirs "));
        events.publish(turn, new Event.Done(MessageState.COMPLETE, false, 0));

        assertThat(healthy).hasSize(2);
        // l'abonné en panne est retiré dès sa première erreur
        assertThat(broken).hasSize(1);
    }

    /** Un événement publié pendant le rejeu passe après les événements rejoués, jamais au milieu. */
    @Test
    void rejeuPuisDirectDansLOrdre() throws InterruptedException {
        events.open(turn, TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());
        events.publish(turn, new Event.Delta("1"));
        events.publish(turn, new Event.Delta("2"));
        List<Event> seen = java.util.Collections.synchronizedList(new ArrayList<>());
        Thread[] live = new Thread[1];
        events.subscribe(turn, e -> {
            seen.add(e);
            if (live[0] == null) {
                live[0] = Thread.ofPlatform().start(() -> events.publish(turn, new Event.Delta("3")));
                try {
                    live[0].join(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        live[0].join(5_000);

        assertThat(seen).containsExactly(new Event.Delta("1"), new Event.Delta("2"), new Event.Delta("3"));
    }

    @Test
    void arretEtOubli() {
        events.open(turn, TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());
        assertThat(events.cancelled(turn)).isFalse();
        events.cancel(turn);
        assertThat(events.cancelled(turn)).isTrue();
        events.publish(turn, new Event.Error("LLM_UNAVAILABLE", "Copilot ne répond pas."));
        clock.advance(Duration.ofMinutes(11));
        events.open(UUID.randomUUID(), TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());
        assertThat(events.subscribe(turn, e -> { }).isEmpty()).isTrue();
    }
}
