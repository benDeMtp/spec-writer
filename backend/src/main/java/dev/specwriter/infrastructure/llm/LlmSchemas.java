package dev.specwriter.infrastructure.llm;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.specwriter.domain.question.QuestionTheme;
import java.util.Arrays;
import java.util.List;

/**
 * Schémas JSON des réponses attendues (contracts/llm-port.md). Copilot les applique en mode strict :
 * toutes les propriétés sont requises et aucune autre n'est permise (StrictJsonSchema).
 */
final class LlmSchemas {

    private static final List<String> THEMES = Arrays.stream(QuestionTheme.values())
            .filter(t -> t != QuestionTheme.CONTRADICTION).map(Enum::name).toList();

    private static final JsonObjectSchema QUESTION = JsonObjectSchema.builder()
            .addProperty("theme", JsonEnumSchema.builder().enumValues(THEMES).build())
            .addStringProperty("label")
            .addStringProperty("rationale")
            .addProperty("suggestions", array(string()))
            .addStringProperty("defaultValue")
            .addProperty("dependsOnQuestionIds", array(string()))
            .build();

    static final ResponseFormat QUESTIONS = format("questions", JsonObjectSchema.builder()
            .addProperty("questions", array(QUESTION))
            .addProperty("contradictions", array(JsonObjectSchema.builder()
                    .addStringProperty("description")
                    .addStringProperty("questionLabel")
                    .build()))
            .addProperty("coveredThemes", array(JsonEnumSchema.builder().enumValues(THEMES).build()))
            .build());

    static final ResponseFormat REASSESS = format("reassess", JsonObjectSchema.builder()
            .addProperty("obsoleteQuestionIds", array(string()))
            .addProperty("newQuestions", array(QUESTION))
            .build());

    static final ResponseFormat SPECIFICATION = format("specification", JsonObjectSchema.builder()
            .addStringProperty("title")
            .addStringProperty("summary")
            .addStringProperty("details")
            .addProperty("useCases", array(JsonObjectSchema.builder()
                    .addStringProperty("title")
                    .addStringProperty("actor")
                    .addStringProperty("goal")
                    .addProperty("preconditions", array(string()))
                    .addProperty("nominalFlow", array(string()))
                    .addProperty("alternatives", array(string()))
                    .addProperty("source", JsonObjectSchema.builder()
                            .addEnumProperty("kind", List.of("NEED_EXCERPT", "ANSWER", "ASSUMPTION", "PROJECT_CONTEXT",
                                    "JIRA_ISSUE", "DOC_PAGE", "ARCH_ELEMENT"))
                            .addStringProperty("questionId")
                            .addStringProperty("excerpt")
                            .addStringProperty("ref")
                            .build())
                    .build()))
            .addProperty("scenarios", array(JsonObjectSchema.builder()
                    .addProperty("useCaseIndex", JsonIntegerSchema.builder().build())
                    .addStringProperty("title")
                    .addEnumProperty("kind", List.of("NOMINAL", "ERROR", "EDGE"))
                    .addStringProperty("asA")
                    .addProperty("given", array(string()))
                    .addProperty("when", array(string()))
                    .addProperty("then", array(string()))
                    .addProperty("assumption", JsonBooleanSchema.builder().build())
                    .build()))
            .addProperty("assumptions", array(JsonObjectSchema.builder()
                    .addStringProperty("statement")
                    .addEnumProperty("origin", List.of("SKIPPED_QUESTION", "TOOL_INFERENCE"))
                    .addStringProperty("questionId")
                    .build()))
            .addProperty("requirements", array(JsonObjectSchema.builder()
                    .addEnumProperty("category", List.of("FUNCTIONAL", "TECHNICAL"))
                    .addStringProperty("statement")
                    .build()))
            .addProperty("adrDeviations", array(JsonObjectSchema.builder()
                    .addStringProperty("adrRef")
                    .addStringProperty("decision")
                    .addStringProperty("deviation")
                    .build()))
            .build());

    /** Tickets proches retenus (feature 002, research.md R23). */
    static final ResponseFormat RELATED_ISSUES = format("related_issues", JsonObjectSchema.builder()
            .addProperty("related", array(JsonObjectSchema.builder()
                    .addStringProperty("key")
                    .addStringProperty("reason")
                    .build()))
            .build());

    /** Comparaison du besoin et d'un ticket (feature 002, research.md R23). */
    static final ResponseFormat ISSUE_COMPARISON = format("issue_comparison", JsonObjectSchema.builder()
            .addProperty("common", array(string()))
            .addProperty("different", array(string()))
            .addEnumProperty("suggestion", List.of("SAME", "DIFFERENT"))
            .build());

    /** Modules impactés et échanges supposés (feature 002, research.md R22). */
    static final ResponseFormat IMPACT = format("impact", JsonObjectSchema.builder()
            .addProperty("modules", array(string()))
            .addProperty("exchanges", array(JsonObjectSchema.builder()
                    .addStringProperty("from")
                    .addStringProperty("to")
                    .addStringProperty("reason")
                    .build()))
            .build());

    private LlmSchemas() {
    }

    private static JsonSchemaElement string() {
        return JsonStringSchema.builder().build();
    }

    private static JsonSchemaElement array(JsonSchemaElement items) {
        return JsonArraySchema.builder().items(items).build();
    }

    private static ResponseFormat format(String name, JsonObjectSchema root) {
        return ResponseFormat.builder().type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder().name(name).rootElement(root).build()).build();
    }
}
