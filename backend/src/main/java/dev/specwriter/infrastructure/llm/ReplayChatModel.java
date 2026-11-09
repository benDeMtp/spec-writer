package dev.specwriter.infrastructure.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rejeu déterministe du LLM pour les tests E2E, actif seulement si {@code specwriter.llm.replay-dir}
 * est défini (profil e2e ; StartupGuard l'interdit ailleurs). Chaque fichier JSON du dossier décrit
 * une réponse : {@code {"operation": "...", "userContains": ["..."], "response": {...}}}. La première
 * entrée dont l'opération et tous les fragments correspondent est renvoyée.
 */
public final class ReplayChatModel implements ChatModel {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern PLACEHOLDER = Pattern.compile("\"__([A-Z_]+?)__\"");

    /** {@code response} : JSON d'une opération structurée ; {@code text} : réponse en texte libre (010). */
    record Entry(String file, String operation, List<String> userContains, String response, String text) {
    }

    private final List<Entry> entries;

    public ReplayChatModel(Path dir) {
        this.entries = load(dir);
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
        Entry e = find(system.toString(), user.toString());
        return ChatResponse.builder().aiMessage(AiMessage.from(resolveQuestionIds(e.response(), user.toString())))
                .build();
    }

    /** Réponse en texte libre rejouée pour une opération en flux (010). */
    String text(String system, String user) {
        Entry e = find(system, user);
        return e.text() != null ? e.text() : e.response();
    }

    private Entry find(String system, String user) {
        String operation = Operations.of(system).orElse("");
        for (Entry e : entries) {
            if (e.operation().equals(operation) && e.userContains().stream().allMatch(user::contains)) {
                return e;
            }
        }
        throw new DomainException(ErrorCode.LLM_UNAVAILABLE, "Aucune réponse rejouée pour l'opération " + operation);
    }

    /**
     * Les identifiants de question sont aléatoires : {@code "__THEME__"} dans une réponse rejouée est
     * remplacé par l'identifiant de la question de ce thème citée dans le message utilisateur.
     */
    static String resolveQuestionIds(String response, String user) {
        Matcher m = PLACEHOLDER.matcher(response);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Matcher id = Pattern.compile("<reponse id=\"([^\"]+)\" theme=\"" + m.group(1) + "\"").matcher(user);
            m.appendReplacement(out, Matcher.quoteReplacement("\"" + (id.find() ? id.group(1) : m.group(1)) + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static List<Entry> load(Path dir) {
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonNode node = JSON.readTree(Files.readString(f));
                List<String> contains = new ArrayList<>();
                node.path("userContains").forEach(n -> contains.add(n.asString()));
                out.add(new Entry(String.valueOf(f.getFileName()), node.path("operation").asString(), contains,
                        JSON.writeValueAsString(node.get("response")),
                        node.hasNonNull("text") ? node.get("text").asString() : null));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier de rejeu illisible", e);
        }
        return out;
    }
}
