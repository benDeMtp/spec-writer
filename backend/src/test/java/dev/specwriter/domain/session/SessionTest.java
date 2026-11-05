package dev.specwriter.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.project.Project;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionTest {

    static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");
    static final Need NEED = Need.of(NeedTest.VALID);

    @Test
    void creationAvecNomParDefautEtLangueDetectee() {
        Session s = Session.start(UUID.randomUUID(), Project.DEFAULT_ID, NEED, null, T0);
        assertThat(s.name()).isEqualTo(NEED.defaultName());
        assertThat(s.language()).isEqualTo(Language.FR);
        assertThat(s.origin()).isEqualTo(SessionOrigin.LOCAL);
        assertThat(s.state()).isEqualTo(SessionState.QUESTIONING);
        assertThat(s.createdAt()).isEqualTo(T0);
    }

    @Test
    void nomDeUnACentVingtCaracteres() {
        assertThat(Session.start(UUID.randomUUID(), Project.DEFAULT_ID, NEED, "  Salles  ", T0).name())
                .isEqualTo("Salles");
        assertThatThrownBy(() -> Session.start(UUID.randomUUID(), Project.DEFAULT_ID, NEED, "x".repeat(121), T0))
                .isInstanceOf(DomainException.class);
        Session s = Session.start(UUID.randomUUID(), Project.DEFAULT_ID, NEED, null, T0);
        assertThatThrownBy(() -> s.rename(" ", T0)).isInstanceOf(DomainException.class);
        assertThat(s.rename("Réservations", T0.plusSeconds(1)).updatedAt()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void cycleDeVie() {
        Session s = Session.start(UUID.randomUUID(), Project.DEFAULT_ID, NEED, null, T0);
        Session ready = s.readyToGenerate(T0);
        assertThat(ready.state()).isEqualTo(SessionState.READY_TO_GENERATE);
        Session generated = ready.generated(T0);
        assertThat(generated.state()).isEqualTo(SessionState.GENERATED);
        assertThat(generated.answerChanged(T0.plusSeconds(5)).state()).isEqualTo(SessionState.QUESTIONING);
        assertThat(generated.answerChanged(T0.plusSeconds(5)).updatedAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void langueModifiableAvantLaGenerationSeulement() {
        Session s = Session.start(UUID.randomUUID(), Project.DEFAULT_ID, NEED, null, T0);
        assertThat(s.withLanguage(Language.EN, T0).language()).isEqualTo(Language.EN);
        assertThatThrownBy(() -> s.readyToGenerate(T0).generated(T0).withLanguage(Language.EN, T0))
                .isInstanceOf(DomainException.class);
    }
}
