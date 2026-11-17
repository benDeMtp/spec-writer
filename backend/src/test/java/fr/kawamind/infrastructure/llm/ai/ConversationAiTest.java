package fr.kawamind.infrastructure.llm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatModel;
import fr.kawamind.domain.conversation.CitedSource;
import fr.kawamind.domain.conversation.MessageRole;
import fr.kawamind.domain.conversation.NeedDraft;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.sources.PerimeterAnalysis;
import fr.kawamind.domain.sources.Qualification;
import fr.kawamind.support.ScriptedChatModel;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Opérations structurées de la conversation sur les AI Services (014, US1) : qualification d'un
 * périmètre, besoin rédigé depuis la discussion, résumé de la mémoire ; schéma de chaque opération,
 * reprise d'une sortie non conforme, refus après les reprises.
 */
@QuarkusTest
class ConversationAiTest {

    static final List<ConversationLlm.Exchange> HISTORY = List.of(
            new ConversationLlm.Exchange(MessageRole.USER, "Comment annuler un avoir ?"));

    @Inject
    ConversationLlm conversation;

    private ScriptedChatModel install(ScriptedChatModel model) {
        QuarkusMock.installMockForType(model, ChatModel.class);
        return model;
    }

    @Test
    void qualificationAvecSonSchemaSansModuleMalForme() {
        ScriptedChatModel model = install(new ScriptedChatModel().then("qualify-perimeter", List.of("<modules>"),
                "{\"modules\":[{\"id\":\"r/libs/a\",\"qualification\":\"IMPACTED\",\"justification\":\" Définit. \"},"
                        + "{\"id\":\"r/libs/b\",\"qualification\":\"PEUT-ETRE\",\"justification\":\"\"}]}"));
        var analysis = PerimeterAnalysis.complete(1, List.of("ContractStatus"), List.of(), List.of(), 1, 0, List.of(), 1,
                List.of(), List.of());

        var qualified = conversation.qualify(new ConversationLlm.QualifyRequest(Language.FR, HISTORY, "Quels modules ?",
                analysis));

        assertThat(qualified).singleElement().satisfies(q -> {
            assertThat(q.qualification()).isEqualTo(Qualification.IMPACTED);
            assertThat(q.justification()).isEqualTo("Définit.");
        });
        assertThat(model.requests().getFirst().parameters().responseFormat().jsonSchema().name())
                .isEqualTo("perimeter_qualification");
    }

    @Test
    void besoinRedigeDepuisLaDiscussion() {
        ScriptedChatModel model = install(new ScriptedChatModel().then("propose-need", List.of("<sources-citees>",
                "ref=\"FACT-7\""), "{\"need\":\"Annuler un avoir émis par erreur.\",\"points\":[{\"theme\":\"ACTORS\","
                        + "\"question\":\"Qui ?\",\"answer\":\"Le gestionnaire.\",\"basis\":\"USER\",\"sourceRef\":\"\"}]}"));

        NeedDraft draft = conversation.draftNeed(new ConversationLlm.DraftRequest(Language.FR, "Fiche", "", HISTORY,
                List.of(new CitedSource(1, SourceKind.TICKET, "FACT-7", "Avoir"))));

        assertThat(draft.need()).isEqualTo("Annuler un avoir émis par erreur.");
        assertThat(draft.points()).hasSize(1);
        assertThat(model.requests().getFirst().parameters().responseFormat().jsonSchema().name())
                .isEqualTo("need_draft");
    }

    @Test
    void resumeVideRedemandePuisRefuse() {
        ScriptedChatModel model = install(new ScriptedChatModel()
                .then("compact-memory", List.of("<memoire-precedente>"), "{\"summary\":\" \"}")
                .then("compact-memory", List.of("La réponse précédente"), "{\"summary\":\"- Résumé.\"}"));
        assertThat(conversation.compact(new ConversationLlm.CompactRequest(Language.FR, "Ancien", HISTORY)))
                .isEqualTo("- Résumé.");
        assertThat(model.requests()).extracting(r -> r.parameters().responseFormat().jsonSchema().name())
                .containsOnly("compact_memory");

        ScriptedChatModel failing = new ScriptedChatModel();
        for (int i = 0; i < 3; i++) {
            failing.then("propose-need", List.of(), "{\"points\":[]}");
        }
        install(failing);
        assertThatThrownBy(() -> conversation.draftNeed(new ConversationLlm.DraftRequest(Language.FR, "Fiche", "",
                HISTORY, List.of()))).isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT));
        assertThat(failing.exhausted()).isTrue();
    }
}
