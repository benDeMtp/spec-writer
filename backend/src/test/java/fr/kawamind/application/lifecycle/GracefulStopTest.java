package fr.kawamind.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.conversation.TurnEvents;
import fr.kawamind.application.conversation.TurnEvents.Event;
import fr.kawamind.application.knowledge.Indexations;
import fr.kawamind.application.knowledge.SynchronizeKnowledge;
import fr.kawamind.domain.conversation.MessageState;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.InMemoryConversations;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryKnowledgeSources;
import fr.kawamind.support.JiraWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T013 : un seul chemin d'arrêt, borné à 5 s d'attente des travaux (019, research.md R4). */
class GracefulStopTest {

    final JiraWorld world = new JiraWorld();
    final TurnEvents turns = new TurnEvents(world.clock);
    final List<Integer> exits = new ArrayList<>();
    final UUID turn = UUID.randomUUID();
    /** Instant où le tour en cours finit, s'il finit. */
    Instant turnEndsAt;
    GracefulStop stop;

    @BeforeEach
    void setUp() {
        SynchronizeKnowledge sync = new SynchronizeKnowledge(world.projects, new InMemoryKnowledgeSources(),
                world.docSources, world.docs, world.folder, world.fileStates, (w, o) -> null, world.jira, world.tracker,
                world.ticketLinks, new InMemoryKnowledgeIndex(), new FakeEmbedding(), world.clock);
        Indexations indexations = new Indexations(sync, world.projects, r -> { }, world.clock, false);
        RunningWork work = new RunningWork(indexations, turns, new LongWorkTracker(), world.projects,
                new InMemoryConversations());
        stop = new GracefulStop(work, exits::add, world.clock, this::pause);
    }

    /** Pause simulée : le temps avance, et le tour finit à son heure. */
    void pause(Duration d) {
        world.clock.advance(d);
        if (turnEndsAt != null && !world.clock.now().isBefore(turnEndsAt)) {
            turns.publish(turn, new Event.Done(MessageState.INCOMPLETE, false, 0));
            turnEndsAt = null;
        }
    }

    @Test
    void sansTravailLArretEstImmediat() {
        Instant begin = world.clock.now();

        assertThat(stop.stop()).isTrue();

        assertThat(exits).containsExactly(0);
        assertThat(world.clock.now()).isEqualTo(begin);
        assertThat(stop.stopping()).isTrue();
    }

    @Test
    void annuleLesToursPuisAttendQuIlsFinissent() {
        turns.open(turn, TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());
        turnEndsAt = world.clock.now().plusSeconds(2);
        Instant begin = world.clock.now();

        stop.stop();

        assertThat(turns.cancelled(turn)).isTrue();
        assertThat(exits).containsExactly(0);
        assertThat(Duration.between(begin, world.clock.now())).isBetween(Duration.ofSeconds(2), Duration.ofMillis(2100));
    }

    @Test
    void lAttenteEstBorneeACinqSecondes() {
        turns.open(turn, TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());
        Instant begin = world.clock.now();

        stop.stop();

        assertThat(exits).containsExactly(0);
        assertThat(Duration.between(begin, world.clock.now())).isBetween(Duration.ofSeconds(5), Duration.ofMillis(5100));
    }

    @Test
    void unSecondArretEstSansEffet() {
        stop.stop();

        assertThat(stop.stop()).isFalse();
        assertThat(exits).containsExactly(0);
    }

    @Test
    void leCrochetDArretInterromptEtAttendSansRappelerLaSortie() {
        turns.open(turn, TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());
        turnEndsAt = world.clock.now().plusSeconds(1);

        stop.onShutdown();

        assertThat(turns.cancelled(turn)).isTrue();
        assertThat(turns.openTurns()).isEmpty();
        assertThat(exits).isEmpty();
    }

    @Test
    void leCrochetApresQuitterNAttendPasUneSecondeFois() {
        stop.stop();
        Instant after = world.clock.now();

        stop.onShutdown();

        assertThat(world.clock.now()).isEqualTo(after);
    }
}
