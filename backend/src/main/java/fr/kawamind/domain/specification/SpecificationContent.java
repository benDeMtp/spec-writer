package fr.kawamind.domain.specification;

import fr.kawamind.domain.scenario.AcceptanceScenario;
import java.util.List;

/**
 * Contenu d'une version de spécification, identifiants attribués (FR-010). Le résumé et les détails
 * sont du Markdown (FR-012b) ; les détails sont vides pour les versions qui précèdent ce champ, comme
 * les écarts aux ADR (FR-037 de 002), l'énoncé de story et le découpage proposé (015, R9) : absents des
 * versions antérieures, ils sont relus {@code null} et vides.
 */
public record SpecificationContent(
        String title,
        String summary,
        String details,
        List<UseCase> useCases,
        List<AcceptanceScenario> scenarios,
        List<Assumption> assumptions,
        List<Requirement> requirements,
        List<AdrDeviation> adrDeviations,
        UserStory story,
        List<String> splitProposal) {

    public static final int MAX_TITLE = 120;
    public static final int MAX_SUMMARY = 4_000;
    public static final int MAX_DETAILS = 20_000;

    public SpecificationContent {
        details = details == null ? "" : details;
        useCases = List.copyOf(useCases);
        scenarios = List.copyOf(scenarios);
        assumptions = List.copyOf(assumptions);
        requirements = List.copyOf(requirements);
        adrDeviations = adrDeviations == null ? List.of() : List.copyOf(adrDeviations);
        splitProposal = UserStory.split(splitProposal);
    }

    /** Sans énoncé de story (versions antérieures à 015). */
    public SpecificationContent(String title, String summary, String details, List<UseCase> useCases,
            List<AcceptanceScenario> scenarios, List<Assumption> assumptions, List<Requirement> requirements,
            List<AdrDeviation> adrDeviations) {
        this(title, summary, details, useCases, scenarios, assumptions, requirements, adrDeviations, null, List.of());
    }

    /** Sans écart aux ADR. */
    public SpecificationContent(String title, String summary, String details, List<UseCase> useCases,
            List<AcceptanceScenario> scenarios, List<Assumption> assumptions, List<Requirement> requirements) {
        this(title, summary, details, useCases, scenarios, assumptions, requirements, List.of());
    }

    /** Sans détails. */
    public SpecificationContent(String title, String summary, List<UseCase> useCases, List<AcceptanceScenario> scenarios,
            List<Assumption> assumptions, List<Requirement> requirements) {
        this(title, summary, "", useCases, scenarios, assumptions, requirements, List.of());
    }

    /** Écart du besoin à un ADR, assumé par l'utilisateur (FR-037) : l'ADR, sa décision, l'écart. */
    public record AdrDeviation(String adrRef, String decision, String deviation) {
    }
}
