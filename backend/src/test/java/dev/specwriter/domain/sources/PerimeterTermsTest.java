package dev.specwriter.domain.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.sources.PerimeterTerms.Rejected;
import dev.specwriter.domain.sources.PerimeterTerms.Rejection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerimeterTermsTest {

    @Test
    void lesTermesSontNettoyesEtDedoublonnesSansTenirCompteDeLaCasse() {
        PerimeterTerms terms = PerimeterTerms.of(List.of(" ContractStatus ", "CONTRACT_STATUS", "contractstatus", "x",
                "y".repeat(101)));
        assertThat(terms.values()).containsExactly("ContractStatus", "CONTRACT_STATUS");
        assertThat(terms.rejected()).containsExactly(new Rejected("contractstatus", Rejection.DUPLICATE),
                new Rejected("x", Rejection.TOO_SHORT), new Rejected("y".repeat(101), Rejection.TOO_LONG));
    }

    @Test
    void auPlusDixTermes() {
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            raw.add("terme" + i);
        }
        PerimeterTerms terms = PerimeterTerms.of(raw);
        assertThat(terms.values()).hasSize(10);
        assertThat(terms.rejected()).extracting(Rejected::reason).containsExactly(Rejection.OVER_LIMIT, Rejection.OVER_LIMIT);
    }

    @Test
    void sansTermeValideLAnalyseEstRefusee() {
        assertThatThrownBy(() -> PerimeterTerms.of(List.of(" ", "a"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PerimeterTerms.of(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void lesLignesVidesSontIgnoreesSansEtreRejetees() {
        PerimeterTerms terms = PerimeterTerms.of(List.of("", "  ", "id"));
        assertThat(terms.values()).containsExactly("id");
        assertThat(terms.rejected()).isEmpty();
    }
}
