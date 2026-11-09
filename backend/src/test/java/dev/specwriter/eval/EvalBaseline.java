package dev.specwriter.eval;

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

    private static final double EPSILON = 1e-9;

    public double required(String metric) {
        return Math.max(minimum.getOrDefault(metric, 0.0), scores.getOrDefault(metric, 0.0) - tolerance);
    }

    public boolean accepts(String metric, double score) {
        return score + EPSILON >= required(metric);
    }
}
