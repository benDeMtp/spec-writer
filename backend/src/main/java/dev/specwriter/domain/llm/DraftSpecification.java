package dev.specwriter.domain.llm;

import java.util.List;

/**
 * Spécification brute renvoyée par le LLM, <strong>sans identifiants</strong> : ils sont attribués
 * par le code (research.md R12). Les scénarios référencent leur use case par son index (base 0).
 */
public record DraftSpecification(
        String title,
        String summary,
        List<DraftUseCase> useCases,
        List<DraftScenario> scenarios,
        List<DraftAssumption> assumptions,
        List<DraftRequirement> requirements) {

    public DraftSpecification {
        useCases = List.copyOf(useCases);
        scenarios = List.copyOf(scenarios);
        assumptions = List.copyOf(assumptions);
        requirements = List.copyOf(requirements);
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

    /** {@code kind} : NEED_EXCERPT, ANSWER ou ASSUMPTION ; {@code questionId} vide si sans objet. */
    public record DraftSource(String kind, String questionId, String excerpt) {
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

    /** {@code origin} : SKIPPED_QUESTION ou TOOL_INFERENCE ; {@code questionId} vide si sans objet. */
    public record DraftAssumption(String statement, String origin, String questionId) {
    }

    /** {@code category} : FUNCTIONAL ou TECHNICAL. */
    public record DraftRequirement(String category, String statement) {
    }
}
