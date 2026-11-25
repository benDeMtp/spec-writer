package fr.kawamind.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 021, research.md R1 : une ancre désigne un élément de la même version, ou n'est pas gardée. */
class AnchorTest {

    static SpecificationContent content() {
        UseCase uc = new UseCase("UC-01", "Émettre un avoir", "gestionnaire", "Créditer un client", List.of(),
                List.of("Ouvrir la facture", "Choisir les lignes", "Saisir le montant"), List.of("Facture soldée"),
                new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "avoir"));
        return new SpecificationContent("Avoirs", "Émission des avoirs.", "", List.of(uc),
                List.of(Specs.scenario("SC-01-02", "UC-01", fr.kawamind.domain.scenario.AcceptanceScenario.Kind.NOMINAL)),
                List.of(), List.of(new Requirement("EF-03", Requirement.Category.FUNCTIONAL, "Tracer l'avoir"),
                        new Requirement("ET-01", Requirement.Category.TECHNICAL, "Journaliser")));
    }

    @Test
    void ancresValides() {
        SpecificationContent c = content();
        assertThat(new Anchor("UC-01", null, false).resolves(c)).isTrue();
        assertThat(new Anchor("UC-01", 2, false).resolves(c)).isTrue();
        assertThat(new Anchor("UC-01", 3, false).resolves(c)).isTrue();
        assertThat(new Anchor("UC-01", 1, true).resolves(c)).isTrue();
        assertThat(new Anchor("SC-01-02", null, false).resolves(c)).isTrue();
        assertThat(new Anchor("EF-03", null, false).resolves(c)).isTrue();
        assertThat(new Anchor("ET-01", null, false).resolves(c)).isTrue();
        assertThat(new Anchor(Anchor.SUMMARY, null, false).resolves(c)).isTrue();
    }

    @Test
    void ancresInvalides() {
        SpecificationContent c = content();
        assertThat(new Anchor("UC-09", null, false).resolves(c)).isFalse();
        assertThat(new Anchor("UC-01", 0, false).resolves(c)).isFalse();
        assertThat(new Anchor("UC-01", 4, false).resolves(c)).isFalse();
        assertThat(new Anchor("UC-01", 2, true).resolves(c)).isFalse();
        assertThat(new Anchor("SC-01-02", 1, false).resolves(c)).isFalse();
        assertThat(new Anchor("EF-03", null, true).resolves(c)).isFalse();
        assertThat(new Anchor(" ", null, false).resolves(c)).isFalse();
    }

    @Test
    void uneAncreInvalideNEstPasGardee() {
        SpecificationContent c = content();
        assertThat(Anchor.orNull(new Anchor("UC-01", 4, false), c)).isNull();
        assertThat(Anchor.orNull(null, c)).isNull();
        assertThat(Anchor.orNull(new Anchor("UC-01", 2, false), c)).isEqualTo(new Anchor("UC-01", 2, false));
    }

    @Test
    void libelle() {
        assertThat(new Anchor("UC-02", 4, false).label(fr.kawamind.domain.session.Language.FR)).isEqualTo("UC-02, étape 4");
        assertThat(new Anchor("UC-02", 1, true).label(fr.kawamind.domain.session.Language.FR))
                .isEqualTo("UC-02, alternative 1");
        assertThat(new Anchor("UC-02", 4, false).label(fr.kawamind.domain.session.Language.EN)).isEqualTo("UC-02, step 4");
        assertThat(new Anchor(Anchor.SUMMARY, null, false).label(fr.kawamind.domain.session.Language.FR)).isEqualTo("Résumé");
        assertThat(new Anchor("EF-03", null, false).label(fr.kawamind.domain.session.Language.FR)).isEqualTo("EF-03");
    }
}
