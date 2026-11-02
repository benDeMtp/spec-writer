package dev.specwriter.domain.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LinkedJiraTicketTest {

    static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");

    static LinkedJiraTicket ticket() {
        return new LinkedJiraTicket(UUID.randomUUID(), "PROJ-12", "https://jira/browse/PROJ-12", "Story", 1, T0,
                ConflictDetector.hash("description"), T0, null, LinkedJiraTicket.SyncState.IN_SYNC);
    }

    @Test
    void formatDeCle() {
        assertThat(LinkedJiraTicket.isKey("PROJ-12")).isTrue();
        assertThat(LinkedJiraTicket.isKey("SPEC_2-1")).isTrue();
        assertThat(LinkedJiraTicket.isKey("proj-12")).isFalse();
        assertThat(LinkedJiraTicket.isKey("PROJ-")).isFalse();
        assertThat(LinkedJiraTicket.isKey("PROJ-1 OR 1=1")).isFalse();
        assertThatThrownBy(() -> new LinkedJiraTicket(UUID.randomUUID(), "x", "u", null, 1, null, null, T0, null,
                LinkedJiraTicket.SyncState.IN_SYNC)).isInstanceOf(DomainException.class);
    }

    @Test
    void etatsDeSynchronisation() {
        LinkedJiraTicket t = ticket();
        assertThat(t.localAhead().syncState()).isEqualTo(LinkedJiraTicket.SyncState.LOCAL_AHEAD);
        assertThat(t.conflict().syncState()).isEqualTo(LinkedJiraTicket.SyncState.CONFLICT);
        LinkedJiraTicket pushed = t.conflict().pushed(3, T0.plusSeconds(60), "h", T0.plusSeconds(61));
        assertThat(pushed.syncState()).isEqualTo(LinkedJiraTicket.SyncState.IN_SYNC);
        assertThat(pushed.linkedVersionNumber()).isEqualTo(3);
        assertThat(pushed.lastKnownUpdated()).isEqualTo(T0.plusSeconds(60));
        assertThat(pushed.lastPushedAt()).isEqualTo(T0.plusSeconds(61));
        LinkedJiraTicket retaken = t.conflict().reimported(4, T0.plusSeconds(90), "h2");
        assertThat(retaken.syncState()).isEqualTo(LinkedJiraTicket.SyncState.IN_SYNC);
        assertThat(retaken.lastPushedAt()).isNull();
    }

    @Test
    void detectionDeConflit() {
        LinkedJiraTicket t = ticket();
        assertThat(ConflictDetector.changedSince(t, T0, "description")).isFalse();
        assertThat(ConflictDetector.changedSince(t, T0.plusSeconds(1), "description")).isTrue();
        assertThat(ConflictDetector.changedSince(t, T0, "description modifiée")).isTrue();
        assertThat(ConflictDetector.hash(null)).isEqualTo(ConflictDetector.hash("")).hasSize(64);
    }
}
