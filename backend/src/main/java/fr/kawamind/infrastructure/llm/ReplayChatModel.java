package fr.kawamind.infrastructure.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.infrastructure.llm.CopilotSdkChatModel.Prompt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rejeu déterministe du LLM pour les tests et l'E2E, actif seulement si {@code specwriter.llm.replay-dir}
 * est défini (StartupGuard l'interdit ailleurs). Chaque fichier JSON du dossier décrit une réponse
 * (contracts/replay-format.md) : {@code {"operation": "...", "userContains": ["..."], "afterTools": n,
 * "response"|"text"|"toolCalls": ...}}. La première entrée, dans l'ordre des noms de fichiers, dont
 * l'opération, l'étape et tous les fragments correspondent est renvoyée.
 */
public final class ReplayChatModel implements ChatModel {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern PLACEHOLDER = Pattern.compile("\"__([A-Z0-9_]+?)__\"");
    private static final Pattern DISCUSSION = Pattern.compile("DISCUSSION_(\\d+)");
    private static final Pattern DISCUSSION_REF = Pattern.compile("ref=\"(discussion:[^\"]+)\"");
    private static final Pattern MODULE = Pattern.compile("\"__MODULE:([^\"]+?)__\"");
    private static final Pattern DOC = Pattern.compile("\"__DOC:([^\"]+?)__\"");
    private static final Pattern RESULT = Pattern.compile("<resultat ");

    /** Appel d'outil rejoué : nom et arguments JSON. */
    record ToolCall(String name, String arguments) {
    }

    /**
     * Exactement un des champs {@code response} (JSON d'une opération structurée), {@code text} (texte
     * libre) ou {@code toolCalls} (appels d'outils) ; {@code afterTools} : nombre de résultats d'outils
     * déjà présents dans la requête, nul pour toute étape ; {@code reasoning} : réflexion rejouée avant
     * la réponse.
     */
    record Entry(String file, String operation, List<String> userContains, Integer afterTools, String response,
            String text, List<ToolCall> toolCalls, String reasoning) {

        boolean matches(String op, String user, int tools) {
            return operation.equals(op) && (afterTools == null || afterTools == tools)
                    && userContains.stream().allMatch(user::contains);
        }
    }

    private final List<Entry> entries;
    private final List<ChatModelListener> listeners;

    public ReplayChatModel(Path dir) {
        this(dir, List.of());
    }

    public ReplayChatModel(Path dir, List<ChatModelListener> listeners) {
        this.entries = load(dir);
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        Prompt prompt = Prompt.of(request);
        Entry e = find(prompt.system(), prompt.user());
        if (e.reasoning() != null) {
            ReasoningSink.current().ifPresent(sink -> sink.accept(e.reasoning()));
        }
        return response(e, prompt.user());
    }

    /** Réponse rejouée pour la requête : appels d'outils, texte ou JSON, substitutions faites. */
    static ChatResponse response(Entry e, String user) {
        if (e.toolCalls() != null) {
            List<ToolExecutionRequest> calls = new ArrayList<>();
            for (ToolCall c : e.toolCalls()) {
                calls.add(ToolExecutionRequest.builder().id("replay-" + (calls.size() + 1) + "-" + countResults(user))
                        .name(c.name()).arguments(resolveQuestionIds(c.arguments(), user)).build());
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(calls)).finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
        String text = e.text() != null ? e.text() : resolveQuestionIds(e.response(), user);
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).finishReason(FinishReason.STOP).build();
    }

    Entry find(String system, String user) {
        String operation = Operations.of(system).orElse("");
        int tools = countResults(user);
        for (Entry e : entries) {
            if (e.matches(operation, user, tools)) {
                return e;
            }
        }
        throw new DomainException(ErrorCode.LLM_UNAVAILABLE, unmatched(operation, user, tools));
    }

    /**
     * Message d'échec (US5) : l'opération, l'étape, et pour l'entrée de cette opération la plus proche,
     * les fragments manquants. Aucun autre contenu du message n'est repris.
     */
    private String unmatched(String operation, String user, int tools) {
        StringBuilder out = new StringBuilder("Aucune réponse rejouée pour l'opération ").append(operation)
                .append(" à l'étape ").append(tools).append(" (résultats d'outils déjà reçus)");
        entries.stream().filter(e -> e.operation().equals(operation))
                .min(Comparator.comparingLong((Entry e) -> missing(e, user).size())
                        .thenComparing(e -> e.afterTools() == null || e.afterTools() == tools ? 0 : 1))
                .ifPresent(best -> {
                    out.append(" ; entrée la plus proche : ").append(best.file());
                    if (best.afterTools() != null && best.afterTools() != tools) {
                        out.append(", attendue à l'étape ").append(best.afterTools());
                    }
                    List<String> missing = missing(best, user);
                    if (!missing.isEmpty()) {
                        out.append(", fragments manquants : ").append(missing);
                    }
                });
        return out.toString();
    }

    private static List<String> missing(Entry e, String user) {
        return e.userContains().stream().filter(f -> !user.contains(f)).toList();
    }

    static int countResults(String user) {
        Matcher m = RESULT.matcher(user);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /**
     * Les identifiants de question sont aléatoires : {@code "__THEME__"} dans une réponse rejouée est
     * remplacé par l'identifiant de la question de ce thème citée dans le message utilisateur ;
     * {@code "__DISCUSSION_n__"} par la référence du n-ième message de la discussion (011).
     */
    static String resolveQuestionIds(String response, String user) {
        Matcher m = PLACEHOLDER.matcher(resolveDocuments(resolveModules(response, user), user));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Matcher discussion = DISCUSSION.matcher(m.group(1));
            if (discussion.matches()) {
                m.appendReplacement(out, Matcher.quoteReplacement("\"" + discussionRef(user,
                        Integer.parseInt(discussion.group(1))) + "\""));
                continue;
            }
            Matcher id = Pattern.compile("<reponse id=\"([^\"]+)\" theme=\"" + m.group(1) + "\"").matcher(user);
            m.appendReplacement(out, Matcher.quoteReplacement("\"" + (id.find() ? id.group(1) : m.group(1)) + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Les identifiants de module contiennent celui, aléatoire, de leur racine (013) :
     * {@code "__MODULE:nom__"} est remplacé par l'identifiant du module de ce nom cité dans le message.
     */
    static String resolveModules(String response, String user) {
        Matcher m = MODULE.matcher(response);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Matcher id = Pattern.compile("<module id=\"([^\"]+)\" nom=\"" + Pattern.quote(m.group(1)) + "\"").matcher(user);
            m.appendReplacement(out, Matcher.quoteReplacement("\"" + (id.find() ? id.group(1) : m.group(1)) + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * La référence d'un document d'un dossier contient l'identifiant, aléatoire, de sa source (016) :
     * {@code "__DOC:titre__"} est remplacé par la référence du document de ce titre cité dans le message.
     */
    static String resolveDocuments(String response, String user) {
        Matcher m = DOC.matcher(response);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Matcher ref = Pattern.compile("<document ref=\"([^\"]+)\" titre=\"" + Pattern.quote(m.group(1)) + "\"")
                    .matcher(user);
            m.appendReplacement(out, Matcher.quoteReplacement("\"" + (ref.find() ? ref.group(1) : m.group(1)) + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String discussionRef(String user, int n) {
        Matcher ref = DISCUSSION_REF.matcher(user);
        for (int i = 0; i < n; i++) {
            if (!ref.find()) {
                return "";
            }
        }
        return ref.group(1);
    }

    private static List<Entry> load(Path dir) {
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                out.add(entry(String.valueOf(f.getFileName()), JSON.readTree(Files.readString(f))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier de rejeu illisible", e);
        }
        return out;
    }

    /** Une entrée invalide fait échouer le chargement, avec le nom du fichier. */
    static Entry entry(String file, JsonNode node) {
        List<String> contains = new ArrayList<>();
        node.path("userContains").forEach(n -> contains.add(n.asString()));
        boolean response = node.hasNonNull("response");
        boolean text = node.hasNonNull("text");
        boolean tools = node.hasNonNull("toolCalls");
        if ((response ? 1 : 0) + (text ? 1 : 0) + (tools ? 1 : 0) != 1) {
            throw new IllegalStateException("Fixture de rejeu " + file
                    + " : exactement un des champs response, text ou toolCalls est attendu");
        }
        if (!node.hasNonNull("operation")) {
            throw new IllegalStateException("Fixture de rejeu " + file + " : champ operation absent");
        }
        List<ToolCall> calls = null;
        if (tools) {
            calls = new ArrayList<>();
            for (JsonNode c : node.get("toolCalls")) {
                if (!c.hasNonNull("name")) {
                    throw new IllegalStateException("Fixture de rejeu " + file + " : appel d'outil sans nom");
                }
                calls.add(new ToolCall(c.get("name").asString(),
                        JSON.writeValueAsString(c.hasNonNull("arguments") ? c.get("arguments") : JSON.createObjectNode())));
            }
        }
        return new Entry(file, node.get("operation").asString(), contains,
                node.hasNonNull("afterTools") ? node.get("afterTools").asInt() : null,
                response ? JSON.writeValueAsString(node.get("response")) : null,
                text ? node.get("text").asString() : null, calls,
                node.hasNonNull("reasoning") ? node.get("reasoning").asString() : null);
    }
}
