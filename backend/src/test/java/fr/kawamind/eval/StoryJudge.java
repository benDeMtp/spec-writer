package fr.kawamind.eval;

import fr.kawamind.domain.specification.StoryPart;
import fr.kawamind.domain.specification.UserStory;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway;
import fr.kawamind.infrastructure.llm.EvalTools;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Juge de l'énoncé de story (015, T077, SC-007) : rôle, capacité et bénéfice tirés du besoin ou des
 * réponses, ou marqués supposés. Une génération sans énoncé est {@code ABSENTE}.
 */
final class StoryJudge {

    static final String ABSENT = "ABSENTE";
    static final String CONFORM = "CONFORME";
    static final List<String> VERDICTS = List.of(CONFORM, "NON_CONFORME");

    private static final String PROMPT = EvalRuns.read(Path.of("src/test/resources/eval/prompts/judge-story.txt"));
    private static final Map<String, Object> SCHEMA = EvalTools.strict(Map.of("type", "object", "properties", Map.of(
            "verdict", Map.of("type", "string", "enum", VERDICTS),
            "reason", Map.of("type", "string"))));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Verdict et motif. */
    record Verdict(String verdict, String reason) {
    }

    private final CopilotSdkGateway gateway;
    private final String model;

    StoryJudge(CopilotSdkGateway gateway, String model) {
        this.gateway = gateway;
        this.model = model;
    }

    Verdict judge(String need, List<EvalRuns.Asked> asked, UserStory story) {
        if (story == null) {
            return new Verdict(ABSENT, "");
        }
        StringBuilder prompt = new StringBuilder("<besoin>\n").append(need).append('\n');
        asked.forEach(a -> prompt.append("Question : ").append(a.label()).append("\nRéponse : ").append(a.answer())
                .append('\n'));
        prompt.append("</besoin>\n<story>\n")
                .append(line("Rôle", story.role(), story.isAssumed(StoryPart.ROLE)))
                .append(line("Capacité", story.capability(), story.isAssumed(StoryPart.CAPABILITY)))
                .append(line("Bénéfice", story.benefit(), story.isAssumed(StoryPart.BENEFIT)))
                .append("</story>");
        for (int attempt = 1; attempt <= 2; attempt++) {
            JsonNode v = JSON.readTree(EvalRuns.retrying(() -> gateway.complete(model, PROMPT, prompt.toString(), SCHEMA)));
            if (VERDICTS.contains(v.path("verdict").asString(""))) {
                return new Verdict(v.path("verdict").asString(), v.path("reason").asString(""));
            }
        }
        throw new IllegalStateException("Le juge n'a pas rendu de verdict sur l'énoncé de story");
    }

    private static String line(String name, String text, boolean assumed) {
        return name + " : " + text + (assumed ? " (supposé)" : "") + '\n';
    }
}
