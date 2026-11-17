package fr.kawamind.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.GenerationRequest;
import fr.kawamind.domain.session.Language;
import fr.kawamind.support.ScriptedChatModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** T162 (FR-012b) : le résumé et les détails de la spécification produite sont du Markdown. */
@io.quarkus.test.junit.QuarkusTest
class LangChainLlmClientTest {

    /** Port de l'application, sur les AI Services (014). */
    @jakarta.inject.Inject
    fr.kawamind.domain.port.LlmClient llm;

    private fr.kawamind.domain.port.LlmClient client(ChatModel model) {
        io.quarkus.test.junit.QuarkusMock.installMockForType(model, ChatModel.class);
        return llm;
    }

    static final String SPEC = """
            {"title": "Paiement", "summary": "Le client paie.\\n\\n||Moyen||Frais||\\n|Carte|0 €|",
             "details": "Barème :\\n\\n||Montant||Frais||\\n|< 10 €|0,10 €|\\n\\nFin.",
             "useCases": [], "scenarios": [], "assumptions": [], "requirements": []}
            """;

    @Test
    void lesTableauxWikiDuLlmDeviennentDesTableauxMarkdown() {
        ScriptedChatModel model = new ScriptedChatModel().then("generate-specification", List.of(), SPEC);

        DraftSpecification draft = client(model)
                .generateSpecification(new GenerationRequest("Payer en ligne", Language.FR, List.of()));

        assertThat(draft.summary()).isEqualTo("Le client paie.\n\n| Moyen | Frais |\n| --- | --- |\n| Carte | 0 € |");
        assertThat(draft.details()).contains("Barème :\n\n| Montant | Frais |\n| --- | --- |\n| < 10 € | 0,10 € |\n\nFin.")
                .doesNotContain("||");
    }

    /** T127 : sous-tâches de la story et lien d'un ticket dans la connaissance transmise (R29 de 002). */
    @Test
    void sousTachesEtLienDansLaConnaissance() {
        ScriptedChatModel model = new ScriptedChatModel().then("generate-specification", List.of(), SPEC);
        ProjectKnowledge knowledge = new ProjectKnowledge("", null, List.of(new ProjectKnowledge.Entry(KnowledgeKind.TICKET,
                null, "FACT-30", 0, "Export comptable", "À faire", false, "Texte", "h", "FACT-20 blocks FACT-30")),
                List.of(), List.of(), List.of(), new ProjectKnowledge.Subtasks("FACT-20", List.of(
                        new ProjectKnowledge.Subtask("FACT-21", "En cours", false, "API de paiement"),
                        new ProjectKnowledge.Subtask("FACT-22", "Terminé", true, "Écran <carte>"))));

        client(model).generateSpecification(new GenerationRequest("Payer en ligne", Language.FR, List.of(),
                "", knowledge));

        ScriptedChatModel.Received call = model.received().getFirst();
        assertThat(call.user())
                .contains("<ticket ref=\"FACT-30\" statut=\"À faire\" ferme=\"non\" lien=\"FACT-20 blocks FACT-30\">")
                .contains("<sous-taches story=\"FACT-20\">\n"
                        + "<sous-tache ref=\"FACT-21\" statut=\"En cours\" ferme=\"non\">API de paiement</sous-tache>\n"
                        + "<sous-tache ref=\"FACT-22\" statut=\"Terminé\" ferme=\"oui\">Écran &lt;carte&gt;</sous-tache>\n"
                        + "</sous-taches>");
        assertThat(call.system()).contains("<sous-taches story>").contains("jamais comme des\ntravaux en cours");
    }

    @Test
    void leSchemaDemandeLesDetails() {
        JsonObjectSchema root = (JsonObjectSchema) LlmSchemas.SPECIFICATION.jsonSchema().rootElement();

        assertThat(root.properties()).containsKeys("summary", "details", "story", "splitProposal");
        JsonObjectSchema story = (JsonObjectSchema) root.properties().get("story");
        assertThat(story.properties()).containsOnlyKeys("role", "capability", "benefit", "assumed");
        JsonObjectSchema revision = (JsonObjectSchema) LlmSchemas.REVISION.jsonSchema().rootElement();
        assertThat(((JsonObjectSchema) revision.properties().get("specification")).properties()).containsKey("story");
    }

    /** L'erreur du modèle reste celle du port, cause comprise ; le journal est celui de LlmCallLog (LlmCallLogTest). */
    @Test
    void lEchecDUnAppelGardeSonErreurMetier() {
        DomainException failure = new DomainException(ErrorCode.LLM_UNAVAILABLE, "indisponible",
                new IllegalStateException("UNAVAILABLE pendant l'envoi du message"));
        ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw failure;
            }
        };
        assertThatThrownBy(() -> client(failing)
                .generateSpecification(new GenerationRequest("Payer en ligne", Language.FR, List.of())))
                .isSameAs(failure);
    }

    /** Chaque appel a le délai configuré (180 s, specwriter.llm.timeout.interactive). */
    @Test
    void delaiDUnAppel() {
        List<java.time.Duration> seen = new ArrayList<>();
        ChatModel recording = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                seen.add(CallTimeout.current());
                throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
            }
        };
        assertThatThrownBy(() -> client(recording).generateSpecification(new GenerationRequest("b", Language.FR,
                List.of()))).isInstanceOf(DomainException.class);
        assertThat(seen).containsExactly(java.time.Duration.ofSeconds(180));
    }
}
