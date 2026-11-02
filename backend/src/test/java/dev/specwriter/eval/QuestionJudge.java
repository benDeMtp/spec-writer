package dev.specwriter.eval;

import dev.specwriter.infrastructure.llm.CopilotSdkGateway;
import dev.specwriter.infrastructure.llm.EvalCopilot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Juge des questions (T137, SC-004) : applique la grille de {@code judge-questions.txt} à chaque
 * question posée. Un verdict manquant relance le jugement une fois, puis arrête l'évaluation.
 */
final class QuestionJudge {

    static final List<String> VERDICTS = List.of("PERTINENTE", "REDONDANTE", "HORS_SUJET");

    private static final String PROMPT = EvalRuns.read(Path.of("src/test/resources/eval/prompts/judge-questions.txt"));
    private static final Map<String, Object> SCHEMA = EvalCopilot.strict(Map.of("type", "object", "properties",
            Map.of("verdicts", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                    "index", Map.of("type", "integer"),
                    "verdict", Map.of("type", "string", "enum", VERDICTS),
                    "reason", Map.of("type", "string")))))));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CopilotSdkGateway gateway;
    private final String model;

    QuestionJudge(CopilotSdkGateway gateway, String model) {
        this.gateway = gateway;
        this.model = model;
    }

    List<EvalRuns.Asked> judge(String need, List<EvalRuns.Asked> asked) {
        StringBuilder prompt = new StringBuilder("<besoin>\n").append(need).append("\n</besoin>\n<questions>\n");
        for (int i = 0; i < asked.size(); i++) {
            prompt.append(i + 1).append(". ").append(asked.get(i).contradiction() ? "[contradiction à trancher] " : "")
                    .append(asked.get(i).label()).append("\n   Réponse : ")
                    .append(asked.get(i).answer()).append('\n');
        }
        prompt.append("</questions>");
        for (int attempt = 1; attempt <= 2; attempt++) {
            Map<Integer, JsonNode> byIndex = new HashMap<>();
            JSON.readTree(gateway.complete(model, PROMPT, prompt.toString(), SCHEMA)).path("verdicts")
                    .forEach(v -> byIndex.put(v.path("index").asInt(), v));
            if (java.util.stream.IntStream.rangeClosed(1, asked.size()).allMatch(i -> byIndex.containsKey(i)
                    && VERDICTS.contains(byIndex.get(i).path("verdict").asString("")))) {
                List<EvalRuns.Asked> out = new ArrayList<>();
                for (int i = 0; i < asked.size(); i++) {
                    JsonNode v = byIndex.get(i + 1);
                    EvalRuns.Asked a = asked.get(i);
                    out.add(new EvalRuns.Asked(a.label(), a.contradiction(), a.answer(), v.path("verdict").asString(),
                            v.path("reason").asString("")));
                }
                return out;
            }
        }
        throw new IllegalStateException("Le juge n'a pas rendu un verdict pour chaque question");
    }
}
