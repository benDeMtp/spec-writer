package dev.specwriter.domain.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.scenario.AcceptanceScenario.Kind;
import dev.specwriter.domain.specification.SourceRef;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.StructureViolations;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Règles 1 à 7 de contracts/scenario-format.md ; la règle 8 est vérifiée par le rendu Markdown. */
class ScenarioStructureValidatorTest {

    static final SourceRef NEED = new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "payer");

    static StructureViolations check(AcceptanceScenario... scenarios) {
        return ScenarioStructureValidator.validate(Specs.with(List.of(Specs.useCase("UC-01", NEED)), List.of(scenarios),
                List.of()));
    }

    static AcceptanceScenario sc(String asA, List<String> given, List<String> when, List<String> then) {
        return new AcceptanceScenario("SC-01-01", "UC-01", "Titre", Kind.NOMINAL, asA, given, when, then, false);
    }

    @Test
    void scenarioValide() {
        assertThat(check(Specs.scenario("SC-01-01", "UC-01", Kind.NOMINAL)).isEmpty()).isTrue();
        assertThat(check(sc("client", List.of(), List.of("je paie"), List.of("c'est payé"))).isEmpty()).isTrue();
    }

    @Test
    void regle1EnTantQueObligatoire() {
        assertThat(check(sc("  ", List.of(), List.of("a"), List.of("b"))).messages()).anyMatch(m -> m.contains("En tant que"));
    }

    @Test
    void regle2QuandObligatoire() {
        assertThat(check(sc("x", List.of(), List.of(), List.of("b"))).messages()).anyMatch(m -> m.contains("Quand"));
        assertThat(check(sc("x", List.of(), List.of(" "), List.of("b"))).messages()).anyMatch(m -> m.contains("Quand"));
        assertThat(check(sc("x", List.of(), List.of("a", ""), List.of("b"))).messages()).anyMatch(m -> m.contains("vide"));
    }

    @Test
    void regle3AlorsObligatoire() {
        assertThat(check(sc("x", List.of(), List.of("a"), List.of())).messages()).anyMatch(m -> m.contains("Alors"));
        assertThat(check(sc("x", List.of(), List.of("a"), List.of("b", " "))).messages()).anyMatch(m -> m.contains("Alors"));
    }

    @Test
    void regle4EtantDonneFacultatifSansEntreeVide() {
        assertThat(check(sc("x", List.of("ok", " "), List.of("a"), List.of("b"))).messages())
                .anyMatch(m -> m.contains("Étant donné"));
    }

    @Test
    void regle5UseCaseExistant() {
        assertThat(check(Specs.scenario("SC-02-01", "UC-02", Kind.NOMINAL)).messages())
                .anyMatch(m -> m.contains("use case référencé"));
    }

    @Test
    void regle6CodeFormatEtUnicite() {
        assertThat(check(Specs.scenario("SC-1-1", "UC-01", Kind.NOMINAL)).messages()).anyMatch(m -> m.contains("SC-<nn>-<nn>"));
        assertThat(check(Specs.scenario("SC-01-01", "UC-01", Kind.NOMINAL), Specs.scenario("SC-01-01", "UC-01", Kind.ERROR))
                .messages()).anyMatch(m -> m.contains("double"));
    }

    @Test
    void regle7MilleCaracteresParChamp() {
        assertThat(check(sc("x", List.of(), List.of("a".repeat(1001)), List.of("b"))).messages())
                .anyMatch(m -> m.contains("1 000"));
        assertThat(check(sc("x", List.of(), List.of("a".repeat(1000)), List.of("b"))).isEmpty()).isTrue();
    }

    @Test
    void titreObligatoire() {
        assertThat(check(new AcceptanceScenario("SC-01-01", "UC-01", " ", Kind.NOMINAL, "x", List.of(), List.of("a"),
                List.of("b"), false)).messages()).anyMatch(m -> m.contains("titre"));
    }
}
