package fr.kawamind.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.conversation.TurnEvents.Event;
import fr.kawamind.application.conversation.TurnEvents.Kind;
import fr.kawamind.application.conversation.TurnEvents.OpenTurn;
import fr.kawamind.domain.conversation.MessageState;
import fr.kawamind.support.FixedClock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T005 : les tours en cours, pour Quitter (019, research.md R4). */
class TurnEventsOpenTurnsTest {

    final TurnEvents events = new TurnEvents(new FixedClock());
    final UUID conversation = UUID.randomUUID();

    @Test
    void leTourOuvertEstListeAvecSaNatureEtSaConversation() {
        UUID reply = UUID.randomUUID();
        UUID revision = UUID.randomUUID();
        events.open(reply, Kind.AGENT_REPLY, conversation);
        events.open(revision, Kind.REVISION, conversation);

        assertThat(events.openTurns()).containsExactlyInAnyOrder(
                new OpenTurn(reply, Kind.AGENT_REPLY, conversation),
                new OpenTurn(revision, Kind.REVISION, conversation));
    }

    @Test
    void unTourFiniNEstPlusListe() {
        UUID turn = UUID.randomUUID();
        events.open(turn, Kind.AGENT_REPLY, conversation);

        events.publish(turn, new Event.Done(MessageState.COMPLETE, false, 10));

        assertThat(events.openTurns()).isEmpty();
    }

    @Test
    void toutAnnulerAnnuleChaqueTourOuvert() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        events.open(a, Kind.AGENT_REPLY, conversation);
        events.open(b, Kind.REVISION, conversation);

        events.cancelAll();

        assertThat(events.cancelled(a)).isTrue();
        assertThat(events.cancelled(b)).isTrue();
    }
}
