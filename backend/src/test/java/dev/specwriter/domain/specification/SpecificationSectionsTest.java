package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Language;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 011 : sections de la spécification discutée, avec les ancres du document affiché. */
class SpecificationSectionsTest {

    static String markdown(Language language) {
        SpecificationContent spec = Specs.with(
                List.of(Specs.useCase("UC-01", null), Specs.useCase("UC-02", null)),
                List.of(Specs.scenario("SC-01-01", "UC-01", AcceptanceScenario.Kind.NOMINAL),
                        Specs.scenario("SC-02-01", "UC-02", AcceptanceScenario.Kind.NOMINAL)),
                List.of());
        return MarkdownRenderer.render(spec, language);
    }

    @Test
    void decoupeSelonLesRubriquesEtLesUseCasesDuDocument() {
        List<SpecificationSections.Section> sections = SpecificationSections.of(markdown(Language.FR));

        assertThat(sections).extracting(SpecificationSections.Section::anchor).containsExactly("introduction",
                "summary", "use-cases", "UC-01", "UC-02", "scenarios", "scenarios-UC-01", "scenarios-UC-02",
                "assumptions", "functional", "technical");
        SpecificationSections.Section uc = sections.get(3);
        assertThat(uc.title()).isEqualTo("UC-01 : Payer la commande");
        assertThat(uc.text()).contains("Régler son panier").doesNotContain("UC-02");
        assertThat(sections.get(6).text()).contains("SC-01-01").doesNotContain("SC-02-01");
        assertThat(sections.getFirst().text()).contains("Paiement en ligne");
        // le texte des sections recompose tout le document
        assertThat(String.join("", sections.stream().map(SpecificationSections.Section::text).toList()))
                .isEqualTo(markdown(Language.FR));
    }

    @Test
    void reconnaitLesRubriquesEnAnglais() {
        assertThat(SpecificationSections.of(markdown(Language.EN))).extracting(SpecificationSections.Section::anchor)
                .contains("summary", "use-cases", "UC-01", "scenarios-UC-02", "functional");
    }

    @Test
    void uneRubriqueInconnueGardeUneAncreLisibleEtUnique() {
        List<SpecificationSections.Section> sections = SpecificationSections.of(
                "# T\n\n## Rubrique libre\n\nA\n\n## Rubrique libre\n\nB\n");

        assertThat(sections).extracting(SpecificationSections.Section::anchor)
                .containsExactly("introduction", "rubrique-libre", "rubrique-libre-2");
    }

    @Test
    void ajusteALaPlaceEnReduisantLesDernieresSectionsAleurTitre() {
        List<SpecificationSections.Section> sections = SpecificationSections.of(markdown(Language.FR));
        int firstTwo = sections.get(0).text().length() + sections.get(1).text().length();

        List<SpecificationSections.Section> fitted = SpecificationSections.fit(sections, firstTwo + 10);

        assertThat(fitted).hasSameSizeAs(sections);
        assertThat(fitted.get(0).partial()).isFalse();
        assertThat(fitted.get(1).partial()).isFalse();
        assertThat(fitted.subList(2, fitted.size())).allSatisfy(s -> {
            assertThat(s.partial()).isTrue();
            assertThat(s.text()).isEqualTo(s.title());
        });
        assertThat(SpecificationSections.fit(sections, Integer.MAX_VALUE)).isEqualTo(sections);
    }
}
