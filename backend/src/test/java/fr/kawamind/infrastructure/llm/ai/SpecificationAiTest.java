package fr.kawamind.infrastructure.llm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatModel;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.EditRequest;
import fr.kawamind.domain.llm.GenerationRequest;
import fr.kawamind.domain.llm.MergeRequest;
import fr.kawamind.domain.llm.StructureImportRequest;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.LlmClient;
import fr.kawamind.domain.session.Language;
import fr.kawamind.support.ScriptedChatModel;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Famille Specification sur les AI Services (014, US1) : génération, modification, fusion,
 * structuration, nouvelle version d'après la discussion, relevé des changements ; garde-fous de
 * structure et de périmètre de révision.
 */
@QuarkusTest
class SpecificationAiTest {

    static final String SPEC = """
            {"title":"Réservation","summary":"||a||b||\\n|1|2|","details":"","useCases":[{"title":"Réserver","actor":"employé",
            "goal":"réserver","preconditions":[],"nominalFlow":["choisir une salle"],"alternatives":[],
            "source":{"kind":"NEED_EXCERPT","questionId":"","excerpt":"réserver une salle","ref":""}}],
            "scenarios":[{"useCaseIndex":0,"title":"Réservation","kind":"NOMINAL","asA":"employé","given":[],
            "when":["je réserve"],"then":["la salle est réservée"],"assumption":false}],"assumptions":[],
            "requirements":[],"adrDeviations":[]}""";

    @Inject
    LlmClient llm;

    @Inject
    ConversationLlm conversation;

    @Test
    void lesQuatreRedactionsRendentUneSpecificationNormalisee() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("generate-specification", List.of("<besoin>"), SPEC)
                .then("edit-specification", List.of("<instruction>"), SPEC)
                .then("merge-conflict", List.of(), SPEC)
                .then("structure-imported-text", List.of(), SPEC);
        QuarkusMock.installMockForType(model, ChatModel.class);
        DraftSpecification generated = llm.generateSpecification(new GenerationRequest("Réserver", Language.FR, List.of()));
        assertThat(generated.summary()).doesNotContain("||");
        llm.editSpecification(new EditRequest("Réserver", Language.FR, List.of(), "# Spec", "Ajoute un cas", null, null));
        llm.mergeConflict(new MergeRequest("Réserver", Language.FR, List.of(), "local", "jira", null));
        llm.structureImportedText(new StructureImportRequest("Texte importé", Language.FR, null));
        assertThat(model.requests()).extracting(r -> r.parameters().responseFormat().jsonSchema().name())
                .containsOnly("specification");
        assertThat(model.exhausted()).isTrue();
    }

    @Test
    void uneSpecificationIllisibleEstRedemandeePuisRefusee() {
        ScriptedChatModel model = new ScriptedChatModel();
        for (int i = 0; i < 3; i++) {
            model.then("generate-specification", List.of(), "null");
        }
        QuarkusMock.installMockForType(model, ChatModel.class);
        assertThatThrownBy(() -> llm.generateSpecification(new GenerationRequest("Réserver", Language.FR, List.of())))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT));
    }

    @Test
    void nouvelleVersionEtReleveDesChangements() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("revise-specification", List.of(), "{\"changed\":false,\"reason\":\"\",\"specification\":null,"
                        + "\"perimeter\":[]}")
                .then("revise-specification", List.of("La réponse précédente"),
                        "{\"changed\":false,\"reason\":\"Rien d'établi\",\"specification\":null,\"perimeter\":"
                                + "[{\"module\":\"billing\",\"qualification\":\"NOT_IMPACTED\",\"justification\":\" x \"}]}")
                .then("list-changes", List.of(), "{\"changes\":[{\"text\":\" Ajouter l'annulation \",\"origin\":\"USER\"},"
                        + "{\"text\":\"ajouter l'annulation\",\"origin\":\"AGENT\"},{\"text\":\"\",\"origin\":\"AGENT\"}]}");
        QuarkusMock.installMockForType(model, ChatModel.class);
        ConversationLlm.Revision revision = conversation.revise(new ConversationLlm.ReviseRequest(Language.FR, "Réserver",
                List.of(), "# Spec", null, "", List.of(), List.of(), null));
        assertThat(revision.changed()).isFalse();
        assertThat(revision.reason()).isEqualTo("Rien d'établi");
        assertThat(revision.perimeter()).singleElement().satisfies(c -> assertThat(c.justification()).isEqualTo("x"));
        assertThat(conversation.changes(new ConversationLlm.ChangesRequest(Language.FR, "# Spec", "", List.of())))
                .extracting(ConversationLlm.ChangeCandidate::text).containsExactly("Ajouter l'annulation");
        assertThat(model.requests()).extracting(r -> r.parameters().responseFormat().jsonSchema().name())
                .containsExactly("revision", "revision", "change_candidates");
    }
}
