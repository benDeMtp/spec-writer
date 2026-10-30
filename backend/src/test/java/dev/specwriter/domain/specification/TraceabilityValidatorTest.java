package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.scenario.AcceptanceScenario.Kind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TraceabilityValidatorTest {

    static StructureViolations check(SourceRef source, List<Assumption> assumptions) {
        return TraceabilityValidator.validate(Specs.with(List.of(Specs.useCase("UC-01", source)),
                List.of(Specs.scenario("SC-01-01", "UC-01", Kind.NOMINAL)), assumptions), Set.of(Specs.Q1));
    }

    @Test
    void chaqueUseCaseADesSources() {
        assertThat(TraceabilityValidator.validate(Specs.valid(), Set.of()).isEmpty()).isTrue();
        assertThat(check(null, List.of()).messages()).anyMatch(m -> m.contains("source"));
        assertThat(check(new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, " "), List.of()).messages())
                .anyMatch(m -> m.contains("extrait"));
    }

    @Test
    void sourceReponseCiteUneQuestionRepondue() {
        assertThat(check(new SourceRef(SourceRef.Kind.ANSWER, Specs.Q1, "carte"), List.of()).isEmpty()).isTrue();
        assertThat(check(new SourceRef(SourceRef.Kind.ANSWER, null, "carte"), List.of()).messages())
                .anyMatch(m -> m.contains("question répondue"));
    }

    @Test
    void sourceHypotheseListeeDansLesHypotheses() {
        SourceRef source = new SourceRef(SourceRef.Kind.ASSUMPTION, null, "paiement en 3 fois");
        assertThat(check(source, List.of()).messages()).anyMatch(m -> m.contains("hypothèses"));
        assertThat(check(source, List.of(new Assumption("H-01", "Le paiement en 3 fois est proposé",
                Assumption.Origin.TOOL_INFERENCE, null))).isEmpty()).isTrue();
        assertThat(check(new SourceRef(SourceRef.Kind.ASSUMPTION, Specs.Q1, ""), List.of(new Assumption("H-01", "x",
                Assumption.Origin.SKIPPED_QUESTION, Specs.Q1))).isEmpty()).isTrue();
    }

    @Test
    void scenarioRattacheEtScenarioNominalAttendu() {
        SourceRef need = new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "payer");
        StructureViolations v = TraceabilityValidator.validate(Specs.with(List.of(Specs.useCase("UC-01", need)),
                List.of(Specs.scenario("SC-01-01", "UC-01", Kind.ERROR), Specs.scenario("SC-02-01", "UC-02", Kind.NOMINAL)),
                List.of()), Set.of());
        assertThat(v.messages()).anyMatch(m -> m.contains("nominal")).anyMatch(m -> m.contains("SC-02-01"));
        assertThat(TraceabilityValidator.validate(Specs.with(List.of(), List.of(), List.of()), Set.of()).messages())
                .anyMatch(m -> m.contains("au moins un use case"));
    }
}
