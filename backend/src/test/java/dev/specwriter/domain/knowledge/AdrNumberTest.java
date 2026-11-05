package dev.specwriter.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Numéro d'un ADR (feature 004, data-model.md). */
class AdrNumberTest {

    @Test
    void numeroDeLaReferenceOuDuTitre() {
        assertThat(AdrNumber.of("facturation/adr/adr-005.html", "ADR-005 : synchrone")).hasValue(5);
        assertThat(AdrNumber.of("decisions/cache.html", "ADR-12 : cache Jira")).hasValue(12);
        assertThat(AdrNumber.of("aurora/adr/0005-trois-domaines.html", "Trois domaines")).hasValue(5);
        assertThat(AdrNumber.of("aurora/adr/trois.html", "5. Trois domaines")).hasValue(5);
        assertThat(AdrNumber.of("https://doc/adr/0017-bff.html#_decision", "17. BFF")).hasValue(17);
    }

    @Test
    void sansNumero() {
        assertThat(AdrNumber.of("guide/installation.html", "Installation")).isEmpty();
        assertThat(AdrNumber.of("decisions/cache.html", "Architecture Decision Record : cache")).isEmpty();
        assertThat(AdrNumber.of(null, null)).isEmpty();
    }

    @Test
    void successeurCite() {
        assertThat(AdrNumber.ofCited("ADR-0005")).isEqualTo(OptionalInt.of(5));
        assertThat(AdrNumber.ofCited("ADR-12")).isEqualTo(OptionalInt.of(12));
        assertThat(AdrNumber.ofCited(null)).isEmpty();
        assertThat(AdrNumber.ofCited("ailleurs")).isEmpty();
    }

    /** 004, US4 : numéros des ADR cités dans un texte. */
    @Test
    void adrCitesDansUnTexte() {
        assertThat(AdrNumber.cited("Ajoute la configuration nginx demandée par l'ADR 0017 et le C4."))
                .containsExactly(17);
        assertThat(AdrNumber.cited("Voir ADR-5, adr 012, ADR n°17 et ADR n° 3 ; l'adr 5 encore."))
                .containsExactlyInAnyOrder(5, 12, 17, 3);
        assertThat(AdrNumber.cited("La version 0017 et l'ADRESSE 17 ne sont pas des ADR.")).isEmpty();
        assertThat(AdrNumber.cited(null)).isEmpty();
    }
}
