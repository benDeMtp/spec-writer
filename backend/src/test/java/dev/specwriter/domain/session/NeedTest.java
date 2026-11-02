package dev.specwriter.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import org.junit.jupiter.api.Test;

class NeedTest {

    static final String VALID = "Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée.";

    @Test
    void besoinValideConserveTelQuel() {
        assertThat(Need.of("  " + VALID + "\n").text()).isEqualTo(VALID);
    }

    @Test
    void moinsDeDixMotsRefuseAvecUnExemple() {
        assertThatThrownBy(() -> Need.of("Réserver une salle de réunion vite"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.NEED_TOO_SHORT);
                    assertThat(e.userMessage()).contains("Exemple");
                });
        assertThatThrownBy(() -> Need.of("   ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Need.of(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void plusDeDixMilleCaracteresRefuse() {
        String longNeed = VALID + " " + "a".repeat(10_000);
        assertThatThrownBy(() -> Need.of(longNeed))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NEED_TOO_LONG));
        assertThat(Need.of(VALID + " " + "b".repeat(10_000 - VALID.length() - 1)).text()).hasSize(10_000);
    }

    @Test
    void nomParDefautAvecLesPremiersMots() {
        assertThat(Need.of(VALID).defaultName()).isEqualTo("Les employés doivent pouvoir réserver une salle de…");
        Need court = Need.of("un deux trois quatre cinq six sept huit neuf dix");
        assertThat(court.defaultName()).isEqualTo("un deux trois quatre cinq six sept huit…");
    }
}
