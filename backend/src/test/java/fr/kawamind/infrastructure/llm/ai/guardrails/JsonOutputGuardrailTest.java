package fr.kawamind.infrastructure.llm.ai.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import fr.kawamind.domain.llm.ImpactResult;
import fr.kawamind.domain.llm.IssueComparison;
import fr.kawamind.domain.llm.RelatedIssuesJudgement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import org.junit.jupiter.api.Test;

/**
 * Garde-fous de sortie (014, research R4) : une sortie conforme rend l'objet du domaine ; une sortie
 * non conforme est redemandée avec un message neutre ; le journal ne garde ni la réponse ni les
 * données.
 */
class JsonOutputGuardrailTest {

    private static OutputGuardrailRequest request(String answer, int users) {
        return request(answer, users, null);
    }

    private static OutputGuardrailRequest request(String answer, int users, String data) {
        MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        memory.add(SystemMessage.from("[operation:judge-related-issues] consignes"));
        for (int i = 0; i < users; i++) {
            memory.add(UserMessage.from("données confidentielles " + i));
        }
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder().aiMessage(AiMessage.from(answer)).build())
                .chatExecutor(new dev.langchain4j.guardrail.ChatExecutor() {
                    @Override
                    public ChatResponse execute() {
                        throw new AssertionError("pas d'appel");
                    }

                    @Override
                    public ChatResponse execute(List<dev.langchain4j.data.message.ChatMessage> messages) {
                        throw new AssertionError("pas d'appel");
                    }
                })
                .requestParams(GuardrailRequestParams.builder().chatMemory(memory).userMessageTemplate("{data}")
                        .variables(data == null ? java.util.Map.of() : java.util.Map.of("data", data)).build())
                .build();
    }

    @Test
    void uneSortieConformeRendLObjetDuDomaine() {
        OutputGuardrailResult result = new RelatedGuardrail().validate(request(
                "{\"related\":[{\"key\":\"FACT-7\",\"reason\":\"Même annulation\"}]}", 1));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.successfulResult()).isInstanceOfSatisfying(RelatedIssuesJudgement.class,
                j -> assertThat(j.related()).extracting(RelatedIssuesJudgement.Related::key).containsExactly("FACT-7"));
        assertThat(new ComparisonGuardrail().validate(request(
                "{\"common\":[],\"different\":[],\"suggestion\":\"SAME\"}", 1)).successfulResult())
                .isInstanceOf(IssueComparison.class);
        assertThat(new ImpactGuardrail().validate(request("{\"modules\":[\"A\"],\"exchanges\":[]}", 1))
                .successfulResult()).isInstanceOf(ImpactResult.class);
    }

    @Test
    void lesTachesSontLuesPuisVerifieesSurLeursDonnees() {
        String data = "<specification>\n<cas-usage code=\"UC-01\" titre=\"T\">But</cas-usage>\n</specification>\n"
                + "<taches-existantes>\n<tache>D&amp;J</tache>\n</taches-existantes>\n<nombre min=\"1\" max=\"2\"/>";
        String ok = "{\"tasks\":[{\"title\":\"A\",\"outcome\":\"R\",\"basisKind\":\"USE_CASE\",\"basisRef\":\"UC-01\","
                + "\"dependsOn\":[]}]}";
        OutputGuardrailResult accepted = new TasksGuardrail().validate(request(ok, 1, data));
        assertThat(accepted.isSuccess()).isTrue();
        assertThat(accepted.successfulResult()).isInstanceOf(fr.kawamind.domain.task.ProposedTasks.class);
        String taken = ok.replace("\"A\"", "\"d&j\"");
        OutputGuardrailResult refused = new TasksGuardrail().validate(request(taken, 1, data));
        assertThat(refused.isRetry()).isTrue();
        assertThat(refused.getReprompt().orElseThrow()).contains("Tâche 1 : ce titre existe déjà.").doesNotContain("d&j");
        assertThat(new TasksGuardrail().validate(request("{\"tasks\":[{\"title\":\"A\"}]}", 1, data)).isRetry())
                .isTrue();
    }

    @Test
    void uneSortieNonConformeEstRedemandeeSansContenuAuJournal() {
        List<LogRecord> logged = Collections.synchronizedList(new ArrayList<>());
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(JsonOutputGuardrail.class.getName());
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(capture);
        try {
            for (String invalid : List.of("pas du JSON secret", "", "{\"related\":[{\"key\":\" \",\"reason\":\"x\"}]}",
                    "{\"common\":[]}")) {
                OutputGuardrailResult result = (invalid.startsWith("{\"common")
                        ? new ComparisonGuardrail() : new RelatedGuardrail()).validate(request(invalid, 2));
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.isRetry()).isTrue();
                assertThat(result.getReprompt()).contains(JsonOutputGuardrail.REPROMPT);
            }
        } finally {
            logger.removeHandler(capture);
        }
        assertThat(logged).isNotEmpty().allSatisfy(r -> assertThat(new SimpleFormatter().formatMessage(r))
                .matches("LLM judge-related-issues : sortie invalide à la tentative 2 \\(\\w+\\)")
                .doesNotContain("secret").doesNotContain("confidentielles"));
    }
}
