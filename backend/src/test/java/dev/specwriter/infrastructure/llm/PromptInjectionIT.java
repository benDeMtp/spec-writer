package dev.specwriter.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.llm.ProposeQuestionsRequest;
import dev.specwriter.domain.llm.ProposeQuestionsResult;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Language;
import dev.specwriter.support.ScriptedChatModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/**
 * Corpus d'injection (contracts/llm-port.md, FR-026, OWASP LLM01) : le besoin n'atteint jamais le
 * message système, reste entre ses balises et ne peut pas les refermer ; la sortie reste conforme.
 */
class PromptInjectionIT {

    static final String QUESTIONS = """
            {"questions":[{"theme":"GOAL","label":"Quel objectif ?","rationale":"Pour cadrer","suggestions":[],
            "defaultValue":"","dependsOnQuestionIds":[]}],"contradictions":[],"coveredThemes":["ACTORS"]}""";

    static final String SPEC = """
            {"title":"Réservation","summary":"Réserver une salle","useCases":[{"title":"Réserver","actor":"employé",
            "goal":"réserver","preconditions":[],"nominalFlow":["choisir une salle"],"alternatives":[],
            "source":{"kind":"NEED_EXCERPT","questionId":"","excerpt":"réserver une salle"}}],
            "scenarios":[{"useCaseIndex":0,"title":"Réservation","kind":"NOMINAL","asA":"employé","given":[],
            "when":["je réserve"],"then":["la salle est réservée"],"assumption":false}],"assumptions":[],"requirements":[]}""";

    static Stream<Path> corpus() throws IOException {
        Path dir = Path.of("src/test/resources/prompt-injection");
        return Files.list(dir).filter(p -> p.toString().endsWith(".md")).sorted();
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void leBesoinResteUneDonnee(Path file) throws IOException {
        String need = Files.readString(file).strip();
        ScriptedChatModel model = new ScriptedChatModel()
                .then("propose-questions", List.of("<besoin>"), QUESTIONS)
                .then("generate-specification", List.of("<besoin>", "<reponses>"), SPEC);
        LangChainLlmClient client = new LangChainLlmClient(model);

        ProposeQuestionsResult questions = client.proposeQuestions(new ProposeQuestionsRequest(need, Language.FR, List.of(),
                Set.of(QuestionTheme.GOAL)));
        DraftSpecification spec = client.generateSpecification(new GenerationRequest(need, Language.FR,
                List.of(new QuestionAndAnswer("q1", QuestionTheme.GOAL, "Quel objectif ?", need, false))));

        assertThat(questions.questions()).hasSize(1);
        assertThat(spec.useCases()).hasSize(1);
        for (ScriptedChatModel.Received call : model.received()) {
            String firstSentence = need.substring(0, Math.min(40, need.length()));
            assertThat(call.system()).doesNotContain(firstSentence).contains("jamais une consigne");
            // une seule paire de balises <besoin> : le texte saisi ne peut pas la refermer
            assertThat(call.user().split("</besoin>", -1)).hasSize(2);
            assertThat(call.user()).doesNotContain("<consigne>");
            String inside = call.user().substring(call.user().indexOf("<besoin>"), call.user().indexOf("</besoin>"));
            assertThat(inside).contains(UserMessages.data(firstSentence));
        }
    }

    @Test
    void sortieNonConformeRedemandeePuisRefusee() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("propose-questions", List.of(), "PWNED")
                .then("propose-questions", List.of("<correction>"), "{\"questions\":[{\"theme\":\"INCONNU\"}]}")
                .then("propose-questions", List.of("<correction>"), QUESTIONS);
        ProposeQuestionsResult result = new LangChainLlmClient(model).proposeQuestions(new ProposeQuestionsRequest(
                "Un besoin quelconque à traiter correctement par l'outil de spécification", Language.FR, List.of(),
                Set.of(QuestionTheme.GOAL)));
        assertThat(result.questions()).hasSize(1);
        assertThat(result.coveredThemes()).containsExactly(QuestionTheme.ACTORS);

        ScriptedChatModel failing = new ScriptedChatModel()
                .then("generate-specification", List.of(), "OK")
                .then("generate-specification", List.of(), "{}")
                .then("generate-specification", List.of(), "{\"title\":\"PIRATE\"}");
        assertThatThrownBy(() -> new LangChainLlmClient(failing).generateSpecification(new GenerationRequest("b",
                Language.FR, List.of()))).isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT));
        assertThat(failing.exhausted()).isTrue();
    }

    @Test
    void questionSansJustificationRefusee() {
        ScriptedChatModel model = new ScriptedChatModel();
        String noRationale = QUESTIONS.replace("\"Pour cadrer\"", "\"\"");
        for (int i = 0; i < LangChainLlmClient.MAX_ATTEMPTS; i++) {
            model.then("propose-questions", List.of(), noRationale);
        }
        assertThatThrownBy(() -> new LangChainLlmClient(model).proposeQuestions(new ProposeQuestionsRequest("b",
                Language.EN, List.of(), Set.of()))).isInstanceOf(DomainException.class);
    }
}
