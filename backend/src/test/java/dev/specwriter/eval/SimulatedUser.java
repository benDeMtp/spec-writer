package dev.specwriter.eval;

import dev.specwriter.domain.question.Question;
import dev.specwriter.infrastructure.llm.CopilotSdkGateway;
import dev.specwriter.infrastructure.llm.EvalCopilot;
import java.nio.file.Path;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Demandeur simulé (T137) : répond aux questions d'après la spécification attendue du golden set. */
final class SimulatedUser {

    record Reply(String answer, boolean skip) {
    }

    private static final String PROMPT = EvalRuns.read(Path.of("src/test/resources/eval/prompts/simulated-user.txt"));
    private static final Map<String, Object> SCHEMA = EvalCopilot.strict(Map.of("type", "object", "properties",
            Map.of("answer", Map.of("type", "string"), "skip", Map.of("type", "boolean"))));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CopilotSdkGateway gateway;
    private final String model;

    SimulatedUser(CopilotSdkGateway gateway, String model) {
        this.gateway = gateway;
        this.model = model;
    }

    Reply answer(String need, String reference, Question question) {
        String prompt = "<besoin>\n" + need + "\n</besoin>\n<reference>\n" + reference + "\n</reference>\n<question>\n"
                + (question.isContradiction() ? "[contradiction à trancher] " : "") + question.label() + (question.suggestions().isEmpty() ? "" : "\nSuggestions : "
                        + String.join(" ; ", question.suggestions())) + "\n</question>";
        JsonNode reply = JSON.readTree(EvalRuns.retrying(() -> gateway.complete(model, PROMPT, prompt, SCHEMA)));
        String answer = reply.path("answer").asString("").strip();
        boolean skip = reply.path("skip").asBoolean(false) || answer.isEmpty();
        // une contradiction n'est jamais passée : la génération resterait bloquée
        return question.isContradiction() && skip ? new Reply(firstSuggestion(question), false) : new Reply(answer, skip);
    }

    private static String firstSuggestion(Question question) {
        return question.suggestions().isEmpty() ? "Je confirme ce que dit la spécification de référence."
                : question.suggestions().getFirst();
    }
}
