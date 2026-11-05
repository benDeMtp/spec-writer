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
}
