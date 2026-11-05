package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Citations ramenées aux sources lues, sans rappeler le LLM ; l'invérifiable est retiré. */
class CitationsTest {

    static final String PAGE = "https://docs.example.com/billing/acteurs.html";
    static final Set<String> KNOWN = Set.of(PAGE, "https://docs.example.com/billing/tva.html", "FACT-12", "Q-1",
            "https://docs.example.com/admin/index.html", "https://docs.example.com/billing/index.html");

    @Test
    void referenceSousUneAutreFormeRamenee() {
        assertThat(Citations.resolver(KNOWN).resolve(PAGE)).isEqualTo(PAGE);
        assertThat(Citations.resolver(KNOWN).resolve("billing/acteurs.html")).isEqualTo(PAGE);
        assertThat(Citations.resolver(KNOWN).resolve("/billing/acteurs.html#gestionnaire")).isEqualTo(PAGE);
        assertThat(Citations.resolver(KNOWN).resolve("<acteurs.html>")).isEqualTo(PAGE);
        assertThat(Citations.resolver(KNOWN).resolve("`" + PAGE + "`.")).isEqualTo(PAGE);
        assertThat(Citations.resolver(KNOWN).resolve("fact-12")).isEqualTo("FACT-12");
        assertThat(Citations.resolver(KNOWN).resolve("q-1")).isEqualTo("Q-1");
        // un dossier local cite des chemins relatifs ; le LLM peut écrire une URL complète
        assertThat(Citations.resolver(Set.of("billing/acteurs.html")).resolve("file:///tmp/site/billing/acteurs.html"))
                .isEqualTo("billing/acteurs.html");
    }

    @Test
    void referenceInconnueOuAmbigueNonRamenee() {
        assertThat(Citations.resolver(KNOWN).resolve("FACT-404")).isNull();
        assertThat(Citations.resolver(KNOWN).resolve("index.html")).isNull();
        assertThat(Citations.resolver(KNOWN).resolve("Page des acteurs")).isNull();
        assertThat(Citations.resolver(KNOWN).resolve("html")).isNull();
    }

    @Test
    void ficheRepareeLigneParLigne() {
        String sheet = """
                ## Acteurs
                - Le comptable lettre les paiements. [source : billing/acteurs.html]
                - Inventé. [source : FACT-404]
                - Mixte. [source : FACT-404, fact-12]
                - Sans citation, gardée telle quelle.
                - Deux groupes. [source : FACT-404] [source : Q-1]""";

        Citations.Repaired r = Citations.repair(sheet, Citations.resolver(KNOWN));

        assertThat(r.markdown()).isEqualTo("""
                ## Acteurs
                - Le comptable lettre les paiements. [source : %s]
                - Mixte. [source : FACT-12]
                - Sans citation, gardée telle quelle.
                - Deux groupes. [source : Q-1]""".formatted(PAGE));
        assertThat(r.renamed()).containsExactlyInAnyOrderEntriesOf(Map.of("billing/acteurs.html", PAGE, "fact-12", "FACT-12"));
        assertThat(r.unknown()).containsExactly("FACT-404");
        assertThat(r.removedLines()).isEqualTo(1);
        assertThat(r.valid()).isEqualTo(3);
        assertThat(r.changed()).isTrue();
    }

    @Test
    void ficheSaineInchangee() {
        String sheet = "## Acteurs\n- Ok. [source : " + PAGE + "]\n";
        Citations.Repaired r = Citations.repair(sheet, Citations.resolver(KNOWN));
        assertThat(r.markdown()).isEqualTo(sheet);
        assertThat(r.changed()).isFalse();
        assertThat(r.valid()).isEqualTo(1);
    }
}
