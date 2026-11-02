package dev.specwriter.domain.specification;

import dev.specwriter.domain.scenario.AcceptanceScenario;
import java.util.List;

/** Contenu d'une version de spécification, identifiants attribués (FR-010). */
public record SpecificationContent(
        String title,
        String summary,
        List<UseCase> useCases,
        List<AcceptanceScenario> scenarios,
        List<Assumption> assumptions,
        List<Requirement> requirements) {

    public static final int MAX_TITLE = 120;

    public SpecificationContent {
        useCases = List.copyOf(useCases);
        scenarios = List.copyOf(scenarios);
        assumptions = List.copyOf(assumptions);
        requirements = List.copyOf(requirements);
    }
}
