package dev.specwriter.domain.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.llm.DraftSpecification.DraftAssumption;
import dev.specwriter.domain.llm.DraftSpecification.DraftRequirement;
import dev.specwriter.domain.llm.DraftSpecification.DraftScenario;
import dev.specwriter.domain.llm.DraftSpecification.DraftSource;
import dev.specwriter.domain.llm.DraftSpecification.DraftUseCase;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Language;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Les échanges avec le LLM sont immuables : une liste modifiée après coup ne change pas la valeur. */
class LlmContractTypesTest {

    private static <T> List<T> mutable(T value) {
        List<T> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    @Test
    void questionsEtResultatsCopiesDefensivement() {
        List<String> suggestions = mutable("Oui");
        ProposedQuestion question = new ProposedQuestion(QuestionTheme.ACTORS, "Qui ?", "Acteur", suggestions, "",
                mutable("q1"));
        suggestions.add("Non");
        assertThat(question.suggestions()).containsExactly("Oui");
        assertThatThrownBy(() -> question.dependsOnQuestionIds().add("x")).isInstanceOf(UnsupportedOperationException.class);

        List<ProposedQuestion> questions = mutable(question);
        ProposeQuestionsResult result = new ProposeQuestionsResult(questions,
                mutable(new Contradiction("A et non A", "Qui ?")), new HashSet<>(Set.of(QuestionTheme.ACTORS)));
        questions.clear();
        assertThat(result.questions()).hasSize(1);
        assertThat(result.contradictions()).hasSize(1);
        assertThat(result.coveredThemes()).containsExactly(QuestionTheme.ACTORS);

        ReassessResult reassess = new ReassessResult(mutable("q1"), mutable(question));
        assertThat(reassess.obsoleteQuestionIds()).containsExactly("q1");
        assertThat(reassess.newQuestions()).containsExactly(question);
    }

    @Test
    void requetesCopiees() {
        QuestionAndAnswer qa = new QuestionAndAnswer("q1", QuestionTheme.ACTORS, "Qui ?", "Un agent", false);
        List<QuestionAndAnswer> answers = mutable(qa);
        Set<QuestionTheme> themes = new HashSet<>(Set.of(QuestionTheme.GOAL));
        ProposeQuestionsRequest propose = new ProposeQuestionsRequest("besoin", Language.FR, answers, themes);
        themes.clear();
        GenerationRequest generation = new GenerationRequest("besoin", Language.FR, answers);
        ReassessRequest reassess = new ReassessRequest("besoin", Language.EN, answers, "q1");
        answers.clear();
        assertThat(propose.known()).containsExactly(qa);
        assertThat(propose.remainingThemes()).containsExactly(QuestionTheme.GOAL);
        assertThat(generation.answers()).containsExactly(qa);
        assertThat(generation.correction()).isEmpty();
        assertThat(generation.withCorrection("UC-01 : source").correction()).isEqualTo("UC-01 : source");
        assertThat(reassess.known()).containsExactly(qa);
    }

    @Test
    void brouillonDeSpecificationCopie() {
        List<String> flow = mutable("Il saisit le besoin");
        DraftUseCase useCase = new DraftUseCase("Saisir", "Agent", "Besoin enregistré", mutable("Connecté"), flow,
                mutable("Annule"), new DraftSource("ANSWER", "q1", "Un agent"));
        DraftScenario scenario = new DraftScenario(0, "Saisie", "NOMINAL", "agent", mutable("connecté"),
                mutable("il saisit"), mutable("le besoin est enregistré"), false);
        flow.add("modifié");
        DraftSpecification spec = new DraftSpecification("Titre", "Résumé", mutable(useCase), mutable(scenario),
                mutable(new DraftAssumption("Hypothèse", "défaut", "q1")),
                mutable(new DraftRequirement("FUNCTIONAL", "Le système enregistre")));
        assertThat(spec.useCases().getFirst().nominalFlow()).containsExactly("Il saisit le besoin");
        assertThat(spec.scenarios().getFirst().then()).containsExactly("le besoin est enregistré");
        assertThat(spec.assumptions()).hasSize(1);
        assertThat(spec.requirements()).hasSize(1);
        List<QuestionAndAnswer> answers = new ArrayList<>();
        EditRequest edit = new EditRequest("besoin", Language.FR, answers, "# Spec", "Ajoute un cas d'erreur", null);
        answers.add(new QuestionAndAnswer("q1", dev.specwriter.domain.question.QuestionTheme.GOAL, "Objectif ?", "x", false));
        assertThat(edit.answers()).isEmpty();
        assertThat(edit.withCorrection("écart").correction()).isEqualTo("écart");
        assertThat(edit.withCorrection("écart").instruction()).isEqualTo("Ajoute un cas d'erreur");
        MergeRequest merge = new MergeRequest("besoin", Language.FR, answers, "local", "jira", null);
        assertThat(merge.withCorrection("écart").jiraMarkdown()).isEqualTo("jira");
        assertThat(merge.withCorrection("écart").correction()).isEqualTo("écart");
        StructureImportRequest structure = new StructureImportRequest("texte", Language.FR, null);
        assertThat(structure.withCorrection("écart")).isEqualTo(new StructureImportRequest("texte", Language.FR, "écart"));
    }
}
