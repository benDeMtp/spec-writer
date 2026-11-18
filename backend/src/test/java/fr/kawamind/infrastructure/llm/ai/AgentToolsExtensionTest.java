package fr.kawamind.infrastructure.llm.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import fr.kawamind.infrastructure.llm.PromptOf;
import fr.kawamind.infrastructure.llm.ReplayModels;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Une capacité s'ajoute par déclaration (014, US3, quickstart 5) : un AI Service, un outil et une
 * consigne de test, une fixture rejouée ; aucune classe de {@code infrastructure.llm} ni
 * {@code Converse} n'est touchée pour les ajouter.
 */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class AgentToolsExtensionTest {

    @Inject
    GlossaryAi glossary;

    @Test
    void lOutilAjouteEstAppelePuisPlafonne(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation": "lookup-glossary", "userContains": ["avoir"], "afterTools": 0,
                 "toolCalls": [{"name": "lookup_glossary", "arguments": {"term": "avoir"}}]}""");
        Files.writeString(dir.resolve("02.json"), """
                {"operation": "lookup-glossary", "userContains": ["FACT-7"], "afterTools": 1,
                 "toolCalls": [{"name": "lookup_glossary", "arguments": {"term": "lettrage"}}]}""");
        Files.writeString(dir.resolve("03.json"), """
                {"operation": "lookup-glossary", "userContains": ["<recherche-arretee>oui</recherche-arretee>"],
                 "afterTools": 2, "text": "Un avoir annule une facture [3]."}""");
        ChatModel replay = ReplayModels.chat(dir);
        List<String[]> prompts = new ArrayList<>();
        QuarkusMock.installMockForType(new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                prompts.add(PromptOf.of(request));
                return replay.chat(request);
            }
        }, ChatModel.class);
        FakeTurn turn = new FakeTurn();
        turn.steps = 4;

        String answer = glossary.ask("tour-test", "<question>\nQu'est-ce qu'un avoir ?\n</question>",
                InvocationParameters.from(Map.of(AgentTools.TURN, turn)));

        assertThat(answer).isEqualTo("Un avoir annule une facture [3].");
        // le premier appel passe par les outils du tour (étape enregistrée), le second est refusé
        assertThat(turn.queries).containsExactly("avoir");
        assertThat(prompts).hasSize(3);
        assertThat(prompts.getFirst()[0]).contains("définis chaque terme du glossaire").doesNotContain("[skill:");
        assertThat(prompts.get(2)[1]).contains("<echange-outils>").contains(SearchCap.STOPPED);
    }
}
