package dev.specwriter.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.session.Language;
import dev.specwriter.support.ScriptedChatModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** T162 (FR-012b) : le résumé et les détails de la spécification produite sont du Markdown. */
class LangChainLlmClientTest {

    static final String SPEC = """
            {"title": "Paiement", "summary": "Le client paie.\\n\\n||Moyen||Frais||\\n|Carte|0 €|",
             "details": "Barème :\\n\\n||Montant||Frais||\\n|< 10 €|0,10 €|\\n\\nFin.",
             "useCases": [], "scenarios": [], "assumptions": [], "requirements": []}
            """;

    @Test
    void lesTableauxWikiDuLlmDeviennentDesTableauxMarkdown() {
        ScriptedChatModel model = new ScriptedChatModel().then("generate-specification", List.of(), SPEC);

        DraftSpecification draft = new LangChainLlmClient(model)
                .generateSpecification(new GenerationRequest("Payer en ligne", Language.FR, List.of()));

        assertThat(draft.summary()).isEqualTo("Le client paie.\n\n| Moyen | Frais |\n| --- | --- |\n| Carte | 0 € |");
        assertThat(draft.details()).contains("Barème :\n\n| Montant | Frais |\n| --- | --- |\n| < 10 € | 0,10 € |\n\nFin.")
                .doesNotContain("||");
    }

    @Test
    void leSchemaDemandeLesDetails() {
        JsonObjectSchema root = (JsonObjectSchema) LlmSchemas.SPECIFICATION.jsonSchema().rootElement();

        assertThat(root.properties()).containsKeys("summary", "details");
    }

    @Test
    void lEchecDUnAppelEstJournaliseAvecLOperationLaTentativeEtLaCause() {
        DomainException failure = new DomainException(ErrorCode.LLM_UNAVAILABLE, "indisponible",
                new IllegalStateException("UNAVAILABLE pendant l'envoi du message"));
        ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw failure;
            }
        };
        List<LogRecord> logged = Collections.synchronizedList(new ArrayList<>());
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(LangChainLlmClient.class.getName());
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
            assertThatThrownBy(() -> new LangChainLlmClient(failing)
                    .generateSpecification(new GenerationRequest("Payer en ligne", Language.FR, List.of())))
                    .isSameAs(failure);
        } finally {
            logger.removeHandler(capture);
        }
        assertThat(logged).anySatisfy(r -> {
            assertThat(r.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
            assertThat(new java.util.logging.SimpleFormatter().formatMessage(r))
                    .matches("LLM generate-specification : échec à la tentative 1 après \\d+ ms \\(LLM_UNAVAILABLE\\)");
            assertThat(r.getThrown()).isSameAs(failure);
        });
        assertThat(logged).noneSatisfy(r -> assertThat(new java.util.logging.SimpleFormatter().formatMessage(r))
                .contains("Payer en ligne"));
    }
}
