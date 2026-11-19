package fr.kawamind.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.knowledge.SourceOrigin;
import fr.kawamind.domain.session.Language;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T041 (016) : écart entre sources, bornes et rubrique « Écarts entre sources ». */
class SourceDeviationTest {

    static final SourceDeviation.Cited DEMANDE = new SourceDeviation.Cited("docs:x/Demande.docx",
            SourceOrigin.DOCUMENTS, "Demande remboursement", Instant.parse("2026-09-12T08:00:00Z"));
    static final SourceDeviation.Cited PAGE = new SourceDeviation.Cited(
            "https://wiki/pages/viewpage.action?pageId=103", SourceOrigin.CONFLUENCE, "Notes de frais", null);

    @Test
    void differenceDeUnAMilleCaracteres() {
        assertThat(new SourceDeviation(DEMANDE, PAGE, "  500 contre 1 000 euros.  ").difference())
                .isEqualTo("500 contre 1 000 euros.");
        assertThatThrownBy(() -> new SourceDeviation(DEMANDE, PAGE, " ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new SourceDeviation(DEMANDE, PAGE, "x".repeat(1_001)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new SourceDeviation.Cited(" ", null, null, null)).isInstanceOf(DomainException.class);
    }

    @Test
    void dixEcartsAuPlusParVersion() {
        SourceDeviation d = new SourceDeviation(DEMANDE, PAGE, "Écart.");
        SpecificationContent base = new SpecificationContent("Rembourser", "Résumé", List.of(), List.of(), List.of(),
                List.of());
        assertThat(base.sourceDeviations()).isEmpty();
        assertThat(base.withSourceDeviations(Collections.nCopies(10, d)).sourceDeviations()).hasSize(10);
        assertThatThrownBy(() -> base.withSourceDeviations(Collections.nCopies(11, d)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rubriqueApresLesEcartsAuxAdrOmiseSansEcart() {
        SpecificationContent base = new SpecificationContent("Rembourser", "Résumé", "", List.of(), List.of(),
                List.of(), List.of(), List.of(new SpecificationContent.AdrDeviation("adr/0005.html", "Bus", "Appel direct")));
        assertThat(MarkdownRenderer.render(base, Language.FR)).doesNotContain("Écarts entre sources");

        String md = MarkdownRenderer.render(base.withSourceDeviations(List.of(new SourceDeviation(DEMANDE, PAGE,
                "La demande plafonne à 500 euros, la page à 1 000 euros."))), Language.FR);

        assertThat(md.indexOf("## Écarts entre sources")).isGreaterThan(md.indexOf("## Écarts aux ADR"));
        assertThat(md).contains("- **Référence** : Demande remboursement (SharePoint, 12 septembre 2026)")
                .contains("  - **Interprétation** : Notes de frais (Confluence, date inconnue)")
                .contains("  - Écart : La demande plafonne à 500 euros, la page à 1 000 euros.");
        assertThat(MarkdownRenderer.render(base.withSourceDeviations(List.of(new SourceDeviation(DEMANDE, PAGE,
                "Ceiling."))), Language.EN)).contains("## Source deviations")
                .contains("(SharePoint, September 12, 2026)").contains("unknown date");
    }
}
