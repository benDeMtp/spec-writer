package fr.kawamind.eval;

import java.util.Map;

/**
 * Base de comparaison ({@code baseline.json}) : un score doit atteindre le plus haut de son minimum
 * (critère de succès de la spec) et de sa base moins la tolérance, qui absorbe la variabilité du LLM.
 * Même règle que {@code ci/check-eval.sh}.
 */
public record EvalBaseline(double tolerance, Map<String, Double> minimum, Map<String, Double> scores) {

    public static final String QUESTION_RELEVANCE = "questionRelevance";
    public static final String SCENARIO_STRUCTURE = "scenarioStructure";
    /** 010 : réponses qui citent la source attendue. */
    public static final String CONVERSATION_CITATION = "conversationCitation";
    /** 010 : questions hors de la connaissance du projet auxquelles l'agent ne répond rien de sourcé. */
    public static final String CONVERSATION_ABSTENTION = "conversationAbstention";
    /** 010 : paires contradictoires signalées avec leurs deux sources. */
    public static final String INCONSISTENCY_RECALL = "inconsistencyRecall";
    /** 011 : réponses qui citent la section attendue de la spécification discutée. */
    public static final String DISCUSSION_CITATION = "discussionCitation";
    /** 011 : écart connu entre la spécification et un ADR, signalé avec ses deux sources. */
    public static final String DISCUSSION_INCONSISTENCY = "discussionInconsistency";
    /** 011 : propositions qui contiennent les changements établis. */
    public static final String REVISION_COMPLETENESS = "revisionCompleteness";
    /** 011 : propositions qui recopient les use cases dont la discussion n'a pas parlé. */
    public static final String REVISION_PRESERVATION = "revisionPreservation";
    /** 011 : propositions qui passent le contrôle de structure. */
    public static final String REVISION_STRUCTURE = "revisionStructure";
    /** 011 : discussions sans changement établi pour lesquelles l'agent ne propose rien. */
    public static final String UNCHANGED_RECALL = "unchangedRecall";
    /** 011 : changements établis relevés comme dits par la personne, avant la rédaction. */
    public static final String CHANGES_RECALL = "changesRecall";
    /** 011 : discussions sans changement établi pour lesquelles rien n'est relevé au nom de la personne. */
    public static final String CHANGES_PRECISION = "changesPrecision";
    /** 013 : modules attendus retrouvés par les termes que l'agent propose. */
    public static final String PERIMETER_TERM_RECALL = "perimeterTermRecall";
    /** 013 : qualifications fermes de l'agent conformes à l'attendu. */
    public static final String PERIMETER_QUALIFICATION = "perimeterQualification";
    /** 013 (T062) : réponse de périmètre qui cite impactés et à vérifier, sans citer les non impactés. */
    public static final String PERIMETER_ANSWER_FOCUS = "perimeterAnswerFocus";
    /** 014 (T019) : part des cas piégés sans fuite du fichier ni de la consigne. */
    public static final String INJECTION_RESISTANCE = "injectionResistance";

    /** 015 (SC-001) : propositions dans les bornes, sans titre dicté par un contenu piégé. */
    public static final String TASK_BOUNDS = "taskBounds";
    /** 015 (SC-001) : tâches dont la base cite un module ou un cas d'usage tel quel. */
    public static final String TASK_BASIS_EXACT = "taskBasisExact";
    /** 015 (SC-001) : modules impactés couverts par au moins une tâche. */
    public static final String TASK_MODULE_COVERAGE = "taskModuleCoverage";
    /** 015 (SC-008) : tâches dont le résultat est jugé vérifiable par un relecteur. */
    public static final String TASK_VERIFIABLE = "taskVerifiable";
    /** 015 (SC-007) : spécifications générées dont l'énoncé de story est jugé conforme. */
    public static final String STORY_STATEMENT = "storyStatement";
    /** 016 (SC-005) : réponses qui suivent la référence et signalent l'écart, sans en inventer. */
    public static final String SOURCE_CONFLICT_FLAGGED = "sourceConflictFlagged";
    /** 016 (SC-007) : recherches dont les extraits de référence pertinents sont retenus. */
    public static final String REFERENCE_RETAINED = "referenceRetained";

    private static final double EPSILON = 1e-9;

    public double required(String metric) {
        return Math.max(minimum.getOrDefault(metric, 0.0), scores.getOrDefault(metric, 0.0) - tolerance);
    }

    public boolean accepts(String metric, double score) {
        return score + EPSILON >= required(metric);
    }
}
