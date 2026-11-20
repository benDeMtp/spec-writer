package fr.kawamind.domain.llm;

import fr.kawamind.domain.specification.UserStory;
import java.util.List;

/**
 * Spécification brute renvoyée par le LLM, <strong>sans identifiants</strong> : ils sont attribués
 * par le code (research.md R12). Les scénarios référencent leur use case par son index (base 0).
 * L'énoncé de story et le découpage proposé (015, R9) sont facultatifs : une story hors bornes rend la
 * réponse non conforme, donc redemandée.
 */
public record DraftSpecification(
        String title,
        String summary,
        String details,
        List<DraftUseCase> useCases,
        List<DraftScenario> scenarios,
        List<DraftAssumption> assumptions,
        List<DraftRequirement> requirements,
        List<DraftAdrDeviation> adrDeviations,
        UserStory story,
        List<String> splitProposal,
        List<DraftSourceDeviation> sourceDeviations,
        List<DraftOpenQuestion> openQuestions) {

    public DraftSpecification {
        details = details == null ? "" : details;
        useCases = List.copyOf(useCases);
        scenarios = List.copyOf(scenarios);
        assumptions = List.copyOf(assumptions);
        requirements = List.copyOf(requirements);
        adrDeviations = adrDeviations == null ? List.of() : List.copyOf(adrDeviations);
        splitProposal = UserStory.split(splitProposal);
        sourceDeviations = sourceDeviations == null ? List.of() : List.copyOf(sourceDeviations);
        openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
    }

    /** Sans point ouvert : toute sortie hors révision, et réponses antérieures à 021. */
    public DraftSpecification(String title, String summary, String details, List<DraftUseCase> useCases,
            List<DraftScenario> scenarios, List<DraftAssumption> assumptions, List<DraftRequirement> requirements,
            List<DraftAdrDeviation> adrDeviations, UserStory story, List<String> splitProposal,
            List<DraftSourceDeviation> sourceDeviations) {
        this(title, summary, details, useCases, scenarios, assumptions, requirements, adrDeviations, story,
                splitProposal, sourceDeviations, List.of());
    }

    /** Sans écart entre sources : réponses antérieures à 016 (fixtures rejouées), R8. */
    public DraftSpecification(String title, String summary, String details, List<DraftUseCase> useCases,
            List<DraftScenario> scenarios, List<DraftAssumption> assumptions, List<DraftRequirement> requirements,
            List<DraftAdrDeviation> adrDeviations, UserStory story, List<String> splitProposal) {
        this(title, summary, details, useCases, scenarios, assumptions, requirements, adrDeviations, story,
                splitProposal, List.of());
    }

    /** Sans énoncé de story : réponses antérieures à 015 (fixtures rejouées), research R9. */
    public DraftSpecification(String title, String summary, String details, List<DraftUseCase> useCases,
            List<DraftScenario> scenarios, List<DraftAssumption> assumptions, List<DraftRequirement> requirements,
            List<DraftAdrDeviation> adrDeviations) {
        this(title, summary, details, useCases, scenarios, assumptions, requirements, adrDeviations, null, List.of());
    }

    /** Sans écart aux ADR. */
    public DraftSpecification(String title, String summary, String details, List<DraftUseCase> useCases,
            List<DraftScenario> scenarios, List<DraftAssumption> assumptions, List<DraftRequirement> requirements) {
        this(title, summary, details, useCases, scenarios, assumptions, requirements, List.of());
    }

    /** Sans détails. */
    public DraftSpecification(String title, String summary, List<DraftUseCase> useCases, List<DraftScenario> scenarios,
            List<DraftAssumption> assumptions, List<DraftRequirement> requirements) {
        this(title, summary, "", useCases, scenarios, assumptions, requirements, List.of());
    }

    public record DraftUseCase(
            String title,
            String actor,
            String goal,
            List<String> preconditions,
            List<String> nominalFlow,
            List<String> alternatives,
            DraftSource source) {

        public DraftUseCase {
            preconditions = List.copyOf(preconditions);
            nominalFlow = List.copyOf(nominalFlow);
            alternatives = List.copyOf(alternatives);
        }
    }

    /**
     * {@code kind} : NEED_EXCERPT, ANSWER, ASSUMPTION, PROJECT_CONTEXT, JIRA_ISSUE, DOC_PAGE ou
     * ARCH_ELEMENT ; {@code questionId} et {@code ref} vides si sans objet.
     */
    public record DraftSource(String kind, String questionId, String excerpt, String ref) {

        public DraftSource(String kind, String questionId, String excerpt) {
            this(kind, questionId, excerpt, "");
        }
    }

    /** Écart du besoin à un ADR de la connaissance : référence, décision de l'ADR, écart (FR-037). */
    public record DraftAdrDeviation(String adrRef, String decision, String deviation) {
    }

    /**
     * Écart entre une source de référence et une interprétation (016, R8) : les deux références
     * citées, telles que transmises, et ce qui diffère.
     */
    public record DraftSourceDeviation(String referenceRef, String interpretationRef, String difference) {
    }

    /** {@code kind} : NOMINAL, ERROR ou EDGE. */
    public record DraftScenario(
            int useCaseIndex,
            String title,
            String kind,
            String asA,
            List<String> given,
            List<String> when,
            List<String> then,
            boolean assumption) {

        public DraftScenario {
            given = List.copyOf(given);
            when = List.copyOf(when);
            then = List.copyOf(then);
        }
    }

    /**
     * {@code origin} : SKIPPED_QUESTION ou TOOL_INFERENCE ; {@code questionId} vide si sans objet ;
     * {@code fromCode} (révision, 021) : code de l'hypothèse dans la version de base, vide si nouvelle.
     */
    public record DraftAssumption(String statement, String origin, String questionId, String fromCode) {

        public DraftAssumption(String statement, String origin, String questionId) {
            this(statement, origin, questionId, "");
        }
    }

    /**
     * Point ouvert (révision, 021, contracts/llm-output.md) : une question que la révision laisse en
     * suspens ; {@code fromCode} : son code dans la version de base, vide si nouvelle.
     */
    public record DraftOpenQuestion(String question, String owner, String fromCode) {
    }

    /** {@code category} : FUNCTIONAL ou TECHNICAL. */
    public record DraftRequirement(String category, String statement) {
    }
}
