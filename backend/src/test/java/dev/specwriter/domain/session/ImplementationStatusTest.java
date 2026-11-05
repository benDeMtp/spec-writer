package dev.specwriter.domain.session;

import static dev.specwriter.domain.session.ImplementationStatus.DRAFT;
import static dev.specwriter.domain.session.ImplementationStatus.IMPLEMENTED;
import static dev.specwriter.domain.session.ImplementationStatus.IN_PROGRESS;
import static dev.specwriter.domain.session.ImplementationStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.session.ImplementationStatus.Actor;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** T047 : transitions du statut d'implémentation, par rôle (data-model.md de 002, FR-026, FR-027). */
class ImplementationStatusTest {

    static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    static final Instant LATER = Instant.parse("2026-11-05T10:00:00Z");

    @ParameterizedTest(name = "{0} → {1} : utilisateur {2}, agent {3}")
    @CsvSource({
            "DRAFT, READY, true, false",
            "READY, DRAFT, true, false",
            "READY, IN_PROGRESS, true, true",
            "IN_PROGRESS, IMPLEMENTED, true, true",
            "IN_PROGRESS, READY, true, false",
            "IN_PROGRESS, DRAFT, true, false",
            "IMPLEMENTED, IN_PROGRESS, true, false",
            "IMPLEMENTED, READY, true, false",
            "IMPLEMENTED, DRAFT, true, false",
            "DRAFT, IN_PROGRESS, true, false",
            "DRAFT, IMPLEMENTED, true, false",
            "READY, IMPLEMENTED, true, false"})
    void tableauDesTransitions(ImplementationStatus from, ImplementationStatus to, boolean user, boolean agent) {
        assertThat(from.allows(to, Actor.USER)).isEqualTo(user);
        assertThat(from.allows(to, Actor.AGENT)).isEqualTo(agent);
    }

    @Test
    void implementedAtRenseigneePuisEffacee() {
        Implementation draft = Implementation.initial();
        assertThat(draft.status()).isEqualTo(DRAFT);
        Implementation done = draft.change(READY, Actor.USER, false, NOW).change(IN_PROGRESS, Actor.AGENT, false, NOW)
                .change(IMPLEMENTED, Actor.AGENT, false, LATER);
        assertThat(done.implementedAt()).isEqualTo(LATER);
        Implementation reopened = done.change(IN_PROGRESS, Actor.USER, true, LATER);
        assertThat(reopened.status()).isEqualTo(IN_PROGRESS);
        assertThat(reopened.implementedAt()).isNull();
    }

    @Test
    void quitterImplementeeExigeUneConfirmationEtEstInterditALAgent() {
        Implementation done = new Implementation(IMPLEMENTED, NOW, null);
        assertThatThrownBy(() -> done.change(READY, Actor.USER, false, LATER)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNLOCK_CONFIRMATION_REQUIRED));
        assertThatThrownBy(() -> done.change(IN_PROGRESS, Actor.AGENT, true, LATER)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TRANSITION_NOT_ALLOWED));
        assertThatThrownBy(() -> Implementation.initial().change(READY, Actor.AGENT, false, LATER)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TRANSITION_NOT_ALLOWED));
    }

    @Test
    void memeStatutSansEffet() {
        Implementation done = new Implementation(IMPLEMENTED, NOW, null);
        assertThat(done.change(IMPLEMENTED, Actor.USER, false, LATER)).isEqualTo(done);
    }
}
