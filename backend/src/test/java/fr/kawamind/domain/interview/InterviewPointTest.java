package fr.kawamind.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.specification.Anchor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 021, data-model.md : un point de séance, ses bornes et ses règles. */
class InterviewPointTest {

    static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");

    static InterviewPoint point(InterviewPoint.Origin origin, String code, String text, InterviewPoint.Outcome outcome,
            String owner) {
        return new InterviewPoint(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), new Anchor("UC-01", 2, false), origin,
                code, text, outcome, owner, null, null, T0, T0);
    }

    @Test
    void texte() {
        assertThat(point(InterviewPoint.Origin.ASSUMPTION, "H-01", "  Plafonné au TTC.  ",
                InterviewPoint.Outcome.CHANGE, null).text()).isEqualTo("Plafonné au TTC.");
        // confirmé ou retiré : le texte est facultatif
        assertThat(point(InterviewPoint.Origin.ASSUMPTION, "H-01", null, InterviewPoint.Outcome.CONFIRMED, null).text())
                .isEmpty();
        assertThat(point(InterviewPoint.Origin.REMARK, null, "", InterviewPoint.Outcome.REMOVE, null).text()).isEmpty();
        assertThatThrownBy(() -> point(InterviewPoint.Origin.REMARK, null, " ", InterviewPoint.Outcome.CHANGE, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> point(InterviewPoint.Origin.REMARK, null, "x".repeat(5_001), InterviewPoint.Outcome.CHANGE,
                null)).isInstanceOf(DomainException.class);
        // un point de la spécification pas encore traité peut être vide
        assertThat(point(InterviewPoint.Origin.ASSUMPTION, "H-01", "", null, null).outcome()).isNull();
    }

    @Test
    void passageCite() {
        InterviewPoint p = new InterviewPoint(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), new Anchor("UC-01", 2, false),
                InterviewPoint.Origin.REMARK, null, "", InterviewPoint.Outcome.REMOVE, null, null, null, T0, T0,
                "  et envoyé au client  ");
        assertThat(p.quote()).isEqualTo("et envoyé au client");
        assertThat(point(InterviewPoint.Origin.REMARK, null, "x", InterviewPoint.Outcome.CHANGE, null).quote()).isNull();
        assertThat(new InterviewPoint(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), null, InterviewPoint.Origin.REMARK,
                null, "x", InterviewPoint.Outcome.CHANGE, null, null, null, T0, T0, "  ").quote()).isNull();
        assertThatThrownBy(() -> new InterviewPoint(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), null,
                InterviewPoint.Origin.REMARK, null, "x", InterviewPoint.Outcome.CHANGE, null, null, null, T0, T0,
                "x".repeat(InterviewPoint.MAX_QUOTE + 1))).isInstanceOf(DomainException.class);
    }

    @Test
    void questionToujoursReportee() {
        InterviewPoint q = point(InterviewPoint.Origin.QUESTION, null, "Qui valide ?", null, "Paul D.");
        assertThat(q.outcome()).isEqualTo(InterviewPoint.Outcome.DEFERRED);
        assertThatThrownBy(() -> point(InterviewPoint.Origin.QUESTION, null, "Qui valide ?",
                InterviewPoint.Outcome.CONFIRMED, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> point(InterviewPoint.Origin.QUESTION, null, " ", null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void codeDOrigine() {
        assertThatThrownBy(() -> point(InterviewPoint.Origin.ASSUMPTION, null, "x", InterviewPoint.Outcome.CHANGE, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> point(InterviewPoint.Origin.SOURCE_DEVIATION, " ", "x", InterviewPoint.Outcome.CHANGE,
                null)).isInstanceOf(DomainException.class);
        assertThat(point(InterviewPoint.Origin.OPEN_QUESTION, "Q-01", "x", InterviewPoint.Outcome.CHANGE, null).originCode())
                .isEqualTo("Q-01");
        // une remarque n'a pas de code d'origine
        assertThat(point(InterviewPoint.Origin.REMARK, "H-01", "x", InterviewPoint.Outcome.CHANGE, null).originCode())
                .isNull();
    }

    @Test
    void responsable() {
        assertThat(point(InterviewPoint.Origin.REMARK, null, "x", InterviewPoint.Outcome.DEFERRED, " ").owner()).isNull();
        assertThatThrownBy(() -> point(InterviewPoint.Origin.REMARK, null, "x", InterviewPoint.Outcome.DEFERRED,
                "x".repeat(121))).isInstanceOf(DomainException.class);
    }

    @Test
    void auPlusDeuxCentsPointsParSpecification() {
        InterviewPoint.requireRoom(199);
        assertThatThrownBy(() -> InterviewPoint.requireRoom(200)).isInstanceOf(DomainException.class);
    }

    @Test
    void decide() {
        assertThat(point(InterviewPoint.Origin.ASSUMPTION, "H-01", "", InterviewPoint.Outcome.CONFIRMED, null).decided())
                .isTrue();
        assertThat(point(InterviewPoint.Origin.ASSUMPTION, "H-01", "x", InterviewPoint.Outcome.DEFERRED, null).decided())
                .isFalse();
        assertThat(point(InterviewPoint.Origin.ASSUMPTION, "H-01", "", null, null).decided()).isFalse();
    }
}
