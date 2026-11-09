package dev.specwriter.domain.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 010, FR-003 : seules les sources du tour sont montrées comme sources. */
class CitationsTest {

    static final List<TurnSource> SOURCES = List.of(
            new TurnSource(1, SourceKind.TICKET, "FACT-7", "Émettre un avoir", "…"),
            new TurnSource(2, SourceKind.ADR, "adr/0005.html", "5. Bus d'événements", "…"),
            new TurnSource(3, SourceKind.SPECIFICATION, "spec:abc@v2", "Saisir un avoir", "…"));

    @Test
    void citationsConnuesGardeesDansLOrdreInconnuesRetirees() {
        Citations.Resolved r = Citations.resolve("Les avoirs passent par le bus [2]. Voir aussi [9] et [1][2].", SOURCES);
        assertThat(r.text()).isEqualTo("Les avoirs passent par le bus [2]. Voir aussi et [1][2].");
        assertThat(r.cited()).extracting(CitedSource::n).containsExactly(2, 1);
        assertThat(r.cited().getFirst()).isEqualTo(new CitedSource(2, SourceKind.ADR, "adr/0005.html", "5. Bus d'événements"));
    }

    @Test
    void sansCitation() {
        Citations.Resolved r = Citations.resolve("Je ne trouve pas cette information dans le projet.", SOURCES);
        assertThat(r.cited()).isEmpty();
        assertThat(r.text()).isEqualTo("Je ne trouve pas cette information dans le projet.");
        // un lien Markdown ou un tableau n'est pas une citation
        assertThat(Citations.resolve("Voir [la page](http://x) et [a]", SOURCES).text()).isEqualTo("Voir [la page](http://x) et [a]");
    }
}
