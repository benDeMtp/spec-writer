package fr.kawamind.domain.specification;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.scenario.AcceptanceScenario;
import java.util.List;

/**
 * Contenu d'une version de spécification, identifiants attribués (FR-010). Le résumé et les détails
 * sont du Markdown (FR-012b) ; les détails sont vides pour les versions qui précèdent ce champ, comme
 * les écarts aux ADR (FR-037 de 002), l'énoncé de story et le découpage proposé (015, R9) : absents des
 * versions antérieures, ils sont relus {@code null} et vides, comme les écarts entre sources (016, R8).
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
        List<String> splitProposal,
        List<SourceDeviation> sourceDeviations,
        List<OpenQuestion> openQuestions) {

    public static final int MAX_TITLE = 120;
    public static final int MAX_SUMMARY = 4_000;
    public static final int MAX_DETAILS = 20_000;
    /** Points ouverts au plus par version (021). */
    public static final int MAX_OPEN_QUESTIONS = 50;

    public SpecificationContent {
        details = details == null ? "" : details;
        useCases = List.copyOf(useCases);
        scenarios = List.copyOf(scenarios);
        assumptions = List.copyOf(assumptions);
        requirements = List.copyOf(requirements);
        adrDeviations = adrDeviations == null ? List.of() : List.copyOf(adrDeviations);
        splitProposal = UserStory.split(splitProposal);
        sourceDeviations = sourceDeviations == null ? List.of() : numbered(sourceDeviations);
        if (sourceDeviations.size() > SourceDeviation.MAX_PER_VERSION) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Une version a au plus 10 écarts entre sources.");
        }
        openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
        if (openQuestions.size() > MAX_OPEN_QUESTIONS) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Une version a au plus 50 points ouverts.");
        }
    }

    /** Sans point ouvert (versions antérieures à 021). */
    public SpecificationContent(String title, String summary, String details, List<UseCase> useCases,
            List<AcceptanceScenario> scenarios, List<Assumption> assumptions, List<Requirement> requirements,
            List<AdrDeviation> adrDeviations, UserStory story, List<String> splitProposal,
            List<SourceDeviation> sourceDeviations) {
        this(title, summary, details, useCases, scenarios, assumptions, requirements, adrDeviations, story,
                splitProposal, sourceDeviations, List.of());
    }

    /** Écarts numérotés par position, {@code EC-01}… (021) : la numérotation suit toujours l'ordre. */
    private static List<SourceDeviation> numbered(List<SourceDeviation> deviations) {
        List<SourceDeviation> out = new java.util.ArrayList<>(deviations.size());
        for (int i = 0; i < deviations.size(); i++) {
            out.add(deviations.get(i).withCode("EC-%02d".formatted(i + 1)));
        }
        return List.copyOf(out);
    }

    /** La même version, avec ces points ouverts. */
    public SpecificationContent withOpenQuestions(List<OpenQuestion> questions) {
        return new SpecificationContent(title, summary, details, useCases, scenarios, assumptions, requirements,
                adrDeviations, story, splitProposal, sourceDeviations, questions);
    }

    /** La même version, avec ces hypothèses. */
    public SpecificationContent withAssumptions(List<Assumption> newAssumptions) {
        return new SpecificationContent(title, summary, details, useCases, scenarios, newAssumptions, requirements,
                adrDeviations, story, splitProposal, sourceDeviations, openQuestions);
    }

    /** Sans écart entre sources (versions antérieures à 016). */
    public SpecificationContent(String title, String summary, String details, List<UseCase> useCases,
            List<AcceptanceScenario> scenarios, List<Assumption> assumptions, List<Requirement> requirements,
            List<AdrDeviation> adrDeviations, UserStory story, List<String> splitProposal) {
        this(title, summary, details, useCases, scenarios, assumptions, requirements, adrDeviations, story,
                splitProposal, List.of());
    }

    /** La même version, avec ces écarts entre sources. */
    public SpecificationContent withSourceDeviations(List<SourceDeviation> deviations) {
        return new SpecificationContent(title, summary, details, useCases, scenarios, assumptions, requirements,
                adrDeviations, story, splitProposal, deviations, openQuestions);
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
