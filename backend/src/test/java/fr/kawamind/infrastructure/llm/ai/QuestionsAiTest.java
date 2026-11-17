package fr.kawamind.infrastructure.llm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatModel;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.llm.ProposeQuestionsRequest;
import fr.kawamind.domain.llm.ProposeQuestionsResult;
import fr.kawamind.domain.llm.ProposedQuestion;
import fr.kawamind.domain.llm.ReassessRequest;
import fr.kawamind.domain.llm.ReassessResult;
import fr.kawamind.domain.port.LlmClient;
import fr.kawamind.domain.question.QuestionTheme;
import fr.kawamind.domain.session.Language;
import fr.kawamind.support.ScriptedChatModel;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Famille Questions sur les AI Services (014, US1) : proposition et réévaluation, garde-fous du thème. */
@QuarkusTest
class QuestionsAiTest {

    static final String QUESTION = "{\"theme\":\"GOAL\",\"label\":\"Quel objectif ?\",\"rationale\":\"Pour cadrer\","
            + "\"suggestions\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"],\"defaultValue\":\"\",\"dependsOnQuestionIds\":[]}";

    @Inject
    LlmClient llm;

    private static ProposeQuestionsRequest propose() {
        return new ProposeQuestionsRequest("Réserver une salle", Language.FR, List.of(),
                EnumSet.of(QuestionTheme.GOAL, QuestionTheme.ACTORS), null);
    }

    @Test
    void propositionAvecSonSchemaEtSuggestionsBornees() {
        ScriptedChatModel model = new ScriptedChatModel().then("propose-questions", List.of("<besoin>"),
                "{\"questions\":[" + QUESTION + "],\"contradictions\":[],\"coveredThemes\":[\"ACTORS\"]}");
        QuarkusMock.installMockForType(model, ChatModel.class);
        ProposeQuestionsResult result = llm.proposeQuestions(propose());
        assertThat(result.questions()).extracting(ProposedQuestion::theme).containsExactly(QuestionTheme.GOAL);
        assertThat(result.questions().getFirst().suggestions()).hasSize(5);
        assertThat(result.coveredThemes()).containsExactly(QuestionTheme.ACTORS);
        assertThat(model.requests().getFirst().parameters().responseFormat().jsonSchema().name()).isEqualTo("questions");
    }

    @Test
    void unThemeInconnuEstRedemande() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("propose-questions", List.of(), "{\"questions\":[" + QUESTION.replace("GOAL", "INCONNU")
                        + "],\"contradictions\":[],\"coveredThemes\":[]}")
                .then("propose-questions", List.of("La réponse précédente"),
                        "{\"questions\":[" + QUESTION + "],\"contradictions\":[],\"coveredThemes\":[]}");
        QuarkusMock.installMockForType(model, ChatModel.class);
        assertThat(llm.proposeQuestions(propose()).questions()).hasSize(1);
        assertThat(model.exhausted()).isTrue();
    }

    @Test
    void reevaluationEtQuestionSansJustificationRefusee() {
        ScriptedChatModel model = new ScriptedChatModel().then("reassess-after-change", List.of("q-1"),
                "{\"obsoleteQuestionIds\":[\"q-2\"],\"newQuestions\":[" + QUESTION + "]}");
        String bad = QUESTION.replace("\"Pour cadrer\"", "\"\"");
        for (int i = 0; i < 3; i++) {
            model.then("propose-questions", List.of(), "{\"questions\":[" + bad + "],\"contradictions\":[],"
                    + "\"coveredThemes\":[]}");
        }
        QuarkusMock.installMockForType(model, ChatModel.class);
        ReassessResult result = llm.reassessAfterChange(new ReassessRequest("Réserver", Language.FR, List.of(), "q-1"));
        assertThat(result.obsoleteQuestionIds()).containsExactly("q-2");
        assertThatThrownBy(() -> llm.proposeQuestions(propose())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT));
    }
}
