package dev.specwriter.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.specwriter.infrastructure.llm.Operations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ChatModel} de test, strictement ordonné : rejoue {@code BTR/llm-scripts/<scénario>/<n>.json}
 * dans l'ordre des noms de fichier et échoue si l'appel ne correspond pas au script (opération
 * attendue et fragments du message utilisateur). Les appels reçus sont conservés pour les assertions.
 */
public class ScriptedChatModel implements ChatModel {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public record Step(String operation, List<String> userContains, String response) {
    }

    public record Received(String system, String user) {
    }

    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<Received> received = new ArrayList<>();

    public static ScriptedChatModel fromScenario(String scenario) {
        try {
            Path dir = Path.of(ScriptedChatModel.class.getResource("/llm-scripts/" + scenario).toURI());
            return fromDirectory(dir);
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Scénario introuvable : " + scenario, e);
        }
    }

    public static ScriptedChatModel fromDirectory(Path dir) {
        ScriptedChatModel model = new ScriptedChatModel();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonNode node = JSON.readTree(Files.readString(f));
                List<String> contains = new ArrayList<>();
                node.path("userContains").forEach(n -> contains.add(n.asString()));
                model.then(node.path("operation").asString(), contains, JSON.writeValueAsString(node.get("response")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return model;
    }

    public ScriptedChatModel then(String operation, List<String> userContains, String response) {
        steps.add(new Step(operation, List.copyOf(userContains), response));
        return this;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        StringBuilder system = new StringBuilder();
        StringBuilder user = new StringBuilder();
        for (ChatMessage m : request.messages()) {
            if (m instanceof SystemMessage s) {
                system.append(s.text());
            } else if (m instanceof UserMessage u) {
                user.append(u.singleText());
            }
        }
        received.add(new Received(system.toString(), user.toString()));
        Step step = steps.poll();
        if (step == null) {
            throw new AssertionError("Appel LLM non prévu par le script : " + Operations.of(system.toString()).orElse("?"));
        }
        String operation = Operations.of(system.toString()).orElse("");
        if (!operation.equals(step.operation())) {
            throw new AssertionError("Opération attendue " + step.operation() + ", reçue " + operation);
        }
        for (String fragment : step.userContains()) {
            if (!user.toString().contains(fragment)) {
                throw new AssertionError("Fragment attendu absent du message utilisateur : " + fragment);
            }
        }
        return ChatResponse.builder().aiMessage(AiMessage.from(step.response())).build();
    }

    public List<Received> received() {
        return List.copyOf(received);
    }

    public boolean exhausted() {
        return steps.isEmpty();
    }
}
