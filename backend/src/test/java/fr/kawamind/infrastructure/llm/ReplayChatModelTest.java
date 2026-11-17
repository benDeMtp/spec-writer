package fr.kawamind.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.support.ScriptedChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayChatModelTest {

    @Test
    void choisitLaReponseSelonOperationEtContenu(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"),
                "{\"operation\":\"propose-questions\",\"userContains\":[\"salle\"],\"response\":{\"q\":1}}");
        Files.writeString(dir.resolve("02.json"),
                "{\"operation\":\"propose-questions\",\"userContains\":[],\"response\":{\"q\":2}}");
        ReplayChatModel model = new ReplayChatModel(dir);

        String a = model.chat(SystemMessage.from("[operation:propose-questions] x"), UserMessage.from("réserver une salle"))
                .aiMessage().text();
        String b = model.chat(SystemMessage.from("[operation:propose-questions] x"), UserMessage.from("autre"))
                .aiMessage().text();
        assertThat(a).isEqualTo("{\"q\":1}");
        assertThat(b).isEqualTo("{\"q\":2}");
        assertThatThrownBy(() -> model.chat(SystemMessage.from("[operation:inconnue]"), UserMessage.from("x")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void operationLueDansLeMarqueur() {
        assertThat(Operations.of("[operation:generate-specification]\nTu es…")).contains("generate-specification");
        assertThat(Operations.of("sans marqueur")).isEmpty();
    }

    @Test
    void scriptStrictementOrdonne() {
        ScriptedChatModel scripted = new ScriptedChatModel()
                .then("a", List.of("x"), "{\"n\":1}")
                .then("b", List.of(), "{\"n\":2}");
        assertThat(scripted.chat(SystemMessage.from("[operation:a]"), UserMessage.from("x")).aiMessage().text()).isEqualTo("{\"n\":1}");
        assertThatThrownBy(() -> scripted.chat(SystemMessage.from("[operation:a]"), UserMessage.from("x")))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void identifiantsDeQuestionResolusDepuisLeMessage() {
        String user = "<reponse id=\"abc-1\" theme=\"GOAL\" hypothese=\"false\"><reponse id=\"def-2\" theme=\"ERRORS\" x";
        assertThat(ReplayChatModel.resolveQuestionIds("{\"ids\":[\"__ERRORS__\",\"__GOAL__\",\"__EDGE_CASES__\"]}", user))
                .isEqualTo("{\"ids\":[\"def-2\",\"abc-1\",\"EDGE_CASES\"]}");
    }

    @Test
    void lesModulesSontDesignesParLeurNom() {
        String user = "<modules>\n<module id=\"r1/libs/common-contract\" nom=\"common-contract\" sorte=\"MAVEN\">"
                + "\n<module id=\"r1/front\" nom=\"demo-front\" sorte=\"NPM\">";
        assertThat(ReplayChatModel.resolveQuestionIds("{\"modules\":[{\"id\":\"__MODULE:common-contract__\"},"
                + "{\"id\":\"__MODULE:inconnu__\"}]}", user))
                .isEqualTo("{\"modules\":[{\"id\":\"r1/libs/common-contract\"},{\"id\":\"inconnu\"}]}");
    }

    @Test
    void lesDocumentsSontDesignesParLeurTitre() {
        String user = "<document ref=\"docs:5f1e/Demande.html\" titre=\"Demande de remboursement\">\n"
                + "<provenance source=\"sharepoint\" maj=\"2026-10-01\" fiabilite=\"reference\"/>";
        assertThat(ReplayChatModel.resolveQuestionIds("{\"ref\":\"__DOC:Demande de remboursement__\","
                + "\"autre\":\"__DOC:Absent__\"}", user))
                .isEqualTo("{\"ref\":\"docs:5f1e/Demande.html\",\"autre\":\"Absent\"}");
    }

    private static final String AGENT = "[operation:converse-agent] Consignes";

    private static ToolExecutionRequest call(String id) {
        return ToolExecutionRequest.builder().id(id).name("search_knowledge").arguments("{}").build();
    }

    @Test
    void appelsDOutilsRejouesSelonLEtape(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation":"converse-agent","userContains":["avoirs"],"afterTools":0,
                 "toolCalls":[{"name":"search_knowledge","arguments":{"query":"__MODULE:billing__","kinds":[]}}]}""");
        Files.writeString(dir.resolve("02.json"), """
                {"operation":"converse-agent","userContains":["avoirs","ADR-12"],"afterTools":1,
                 "text":"Les avoirs passent par le bus [1]."}""");
        ReplayChatModel model = new ReplayChatModel(dir);

        ChatResponse first = model.chat(SystemMessage.from(AGENT),
                UserMessage.from("<question>avoirs</question><module id=\"r1/billing\" nom=\"billing\""));
        assertThat(first.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        ToolExecutionRequest request = first.aiMessage().toolExecutionRequests().get(0);
        assertThat(request.name()).isEqualTo("search_knowledge");
        assertThat(request.arguments()).isEqualTo("{\"query\":\"r1/billing\",\"kinds\":[]}");
        assertThat(request.id()).isNotBlank();

        ChatResponse second = model.chat(SystemMessage.from(AGENT), UserMessage.from("<question>avoirs</question>"),
                AiMessage.from(List.of(call("a"))), ToolExecutionResultMessage.from(call("a"), "<source>ADR-12</source>"));
        assertThat(second.aiMessage().text()).isEqualTo("Les avoirs passent par le bus [1].");
        assertThat(second.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void lEchecNommeLOperationLEtapeEtLesFragmentsManquants(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("10-agent.json"), """
                {"operation":"converse-agent","userContains":["avoirs","ADR-12"],"afterTools":1,"text":"ok"}""");
        ReplayChatModel model = new ReplayChatModel(dir);
        assertThatThrownBy(() -> model.chat(SystemMessage.from(AGENT), UserMessage.from("avoirs confidentiels")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).userMessage()).asString()
                .contains("converse-agent", "étape 0", "10-agent.json", "attendue à l'étape 1", "[ADR-12]")
                .doesNotContain("confidentiels");
    }

    @Test
    void uneEntreeAvecPlusieursReponsesEstRefuseeAuChargement(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("07-double.json"), """
                {"operation":"converse-agent","userContains":[],"text":"a","toolCalls":[{"name":"x"}]}""");
        assertThatThrownBy(() -> new ReplayChatModel(dir)).hasMessageContaining("07-double.json")
                .hasMessageContaining("exactement un");
        Files.writeString(dir.resolve("07-double.json"), """
                {"operation":"converse-agent","userContains":[]}""");
        assertThatThrownBy(() -> new ReplayChatModel(dir)).hasMessageContaining("07-double.json");
    }

    @Test
    void lesFixturesDuDepotSontValides() {
        assertThat(new ReplayChatModel(Path.of("src/test/resources/llm-replay"))).isNotNull();
        assertThat(new ReplayChatModel(Path.of("../e2e/fixtures/llm"))).isNotNull();
    }
}
