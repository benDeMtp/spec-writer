package fr.kawamind.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T007 : les générations synchrones en cours (019, research.md R4). */
class LongWorkTrackerTest {

    final LongWorkTracker tracker = new LongWorkTracker();
    final UUID project = UUID.randomUUID();

    @Test
    void uneGenerationEstCompteeJusquASaFin() {
        LongWorkTracker.Ticket ticket = tracker.begin(WorkKind.GENERATION, project);

        assertThat(tracker.current()).containsExactly(new LongWorkTracker.Entry(WorkKind.GENERATION, project));

        tracker.end(ticket);
        assertThat(tracker.current()).isEmpty();
    }

    @Test
    void deuxGenerationsSimultaneesComptentDeuxFois() {
        LongWorkTracker.Ticket first = tracker.begin(WorkKind.GENERATION, project);
        tracker.begin(WorkKind.GENERATION, project);

        assertThat(tracker.current()).hasSize(2);
        tracker.end(first);
        assertThat(tracker.current()).hasSize(1);
    }

    @Test
    void finirDeuxFoisEstSansEffet() {
        LongWorkTracker.Ticket ticket = tracker.begin(WorkKind.GENERATION, null);
        tracker.end(ticket);
        tracker.end(ticket);

        assertThat(tracker.current()).isEmpty();
    }
}
