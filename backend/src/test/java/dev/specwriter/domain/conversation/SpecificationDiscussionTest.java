package dev.specwriter.domain.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 011 : rattachement d'une conversation à une spécification. */
class SpecificationDiscussionTest {

    static final Instant T = Instant.parse("2026-11-11T09:00:00Z");

    @Test
    void suivieRetireeEtDetachee() {
        UUID c = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        SpecificationDiscussion d = SpecificationDiscussion.start(c, s, "  Réserver une salle ", T);

        assertThat(d.current()).isTrue();
        assertThat(d.specificationName()).isEqualTo("Réserver une salle");
        assertThat(d.sessionId()).isEqualTo(s);
        assertThat(d.attached()).isTrue();
        assertThat(d.retired().current()).isFalse();
        assertThat(d.retired().sessionId()).isEqualTo(s);
        SpecificationDiscussion detached = d.detached();
        assertThat(detached.attached()).isFalse();
        assertThat(detached.current()).isFalse();
        assertThat(detached.specificationName()).isEqualTo("Réserver une salle");
    }

    @Test
    void nomBorneA200() {
        UUID c = UUID.randomUUID();
        assertThat(SpecificationDiscussion.start(c, UUID.randomUUID(), "x".repeat(250), T).specificationName())
                .hasSize(200);
        assertThatThrownBy(() -> SpecificationDiscussion.start(c, UUID.randomUUID(), " ", T))
                .isInstanceOf(DomainException.class);
    }
}
