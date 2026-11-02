package dev.specwriter.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.session.Language;
import dev.specwriter.support.ScriptedChatModel;
import java.util.List;
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
}
