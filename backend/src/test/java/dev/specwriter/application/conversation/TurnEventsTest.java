package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.conversation.TurnEvents.Event;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.support.FixedClock;
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
        events.open(turn);
        events.publish(turn, new Event.Step(new SearchStep(1, "avoirs", Set.of(SourceKind.TICKET), 3)));
        events.publish(turn, new Event.Delta("Les avoirs "));
        List<Event> seen = new ArrayList<>();
        try (TurnEvents.Subscription s = events.subscribe(turn, seen::add).orElseThrow()) {
            events.publish(turn, new Event.Delta("passent par le bus."));
            events.publish(turn, new Event.Done(MessageState.COMPLETE, false));
            assertThat(s.finished()).isTrue();
        }
        assertThat(seen).hasSize(4);
        assertThat(seen.getFirst()).isInstanceOf(Event.Step.class);
        assertThat(seen.getLast()).isEqualTo(new Event.Done(MessageState.COMPLETE, false));
        // un abonné après la fin reçoit tout, et sait que c'est fini
        List<Event> late = new ArrayList<>();
        assertThat(events.subscribe(turn, late::add).orElseThrow().finished()).isTrue();
        assertThat(late).hasSize(4);
        assertThat(events.subscribe(UUID.randomUUID(), late::add)).isEmpty();
    }

    @Test
    void arretEtOubli() {
        events.open(turn);
        assertThat(events.cancelled(turn)).isFalse();
        events.cancel(turn);
        assertThat(events.cancelled(turn)).isTrue();
        events.publish(turn, new Event.Error("LLM_UNAVAILABLE", "Copilot ne répond pas."));
        clock.advance(Duration.ofMinutes(11));
        events.open(UUID.randomUUID());
        assertThat(events.subscribe(turn, e -> { }).isEmpty()).isTrue();
    }
}
