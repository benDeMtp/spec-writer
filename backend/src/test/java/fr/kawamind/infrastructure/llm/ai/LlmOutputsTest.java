package fr.kawamind.infrastructure.llm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.specification.StoryPart;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

/** T067 (015, R9) : l'énoncé de story est facultatif à la lecture, borné quand il est présent. */
public class LlmOutputsTest {

    static final String WITHOUT = SpecificationAiTest.SPEC;

    public static String with(String story, String split) {
        return WITHOUT.substring(0, WITHOUT.lastIndexOf('}')) + ",\"story\":" + story + ",\"splitProposal\":" + split + "}";
    }

    @Test
    void uneReponseSansStoryResteLisible() {
        DraftSpecification draft = LlmOutputs.draft(WITHOUT);
        assertThat(draft.story()).isNull();
        assertThat(draft.splitProposal()).isEmpty();
    }

    @Test
    void lEnonceEtLeDecoupageSontLus() {
        DraftSpecification draft = LlmOutputs.draft(with("""
                {"role":"employé","capability":"réserver une salle","benefit":"tenir ses réunions","assumed":["BENEFIT"]}""",
                "[\"Réserver\",\"Annuler\"]"));
        assertThat(draft.story().role()).isEqualTo("employé");
        assertThat(draft.story().assumed()).containsExactly(StoryPart.BENEFIT);
        assertThat(draft.splitProposal()).containsExactly("Réserver", "Annuler");
    }

    @Test
    void uneStoryHorsBornesRendLaReponseNonConforme() {
        String vide = """
                {"role":"","capability":"réserver","benefit":"b","assumed":[]}""";
        String partie = """
                {"role":"r","capability":"réserver","benefit":"b","assumed":["AUTRE"]}""";
        for (String json : new String[] {with(vide, "[]"), with(partie, "[]"),
                with("{\"role\":\"r\",\"capability\":\"c\",\"benefit\":\"b\",\"assumed\":[]}", "[\"seule\"]")}) {
            assertThatThrownBy(() -> LlmOutputs.draft(json)).isInstanceOfAny(JacksonException.class,
                    IllegalArgumentException.class, fr.kawamind.domain.error.DomainException.class);
        }
    }

    static String questions(String label, String contradiction) {
        return "{\"questions\":[{\"theme\":\"ERRORS\",\"label\":\"" + label + "\",\"rationale\":\"r\","
                + "\"suggestions\":[],\"defaultValue\":\"\",\"dependsOnQuestionIds\":[]}],\"contradictions\":["
                + contradiction + "],\"coveredThemes\":[]}";
    }

    /** Une question ou une contradiction hors bornes est redemandée, au lieu d'échouer après le garde-fou. */
    @Test
    void uneQuestionOuUneContradictionHorsBornesRendLaReponseNonConforme() {
        assertThat(LlmOutputs.questions(questions("Quel délai ?", "{\"description\":\"d\",\"questionLabel\":\"q\"}"))
                .contradictions()).hasSize(1);
        String tooLong = "x".repeat(fr.kawamind.domain.question.Question.MAX_LABEL + 1);
        for (String json : new String[] {questions(tooLong, ""),
                questions("Quel délai ?", "{\"description\":\"d\",\"questionLabel\":\"\"}"),
                questions("Quel délai ?", "{\"description\":\"\",\"questionLabel\":\"q\"}"),
                questions("Quel délai ?", "{\"description\":\"d\",\"questionLabel\":\"" + tooLong + "\"}")}) {
            assertThatThrownBy(() -> LlmOutputs.questions(json)).isInstanceOf(IllegalArgumentException.class);
        }
        String reassess = "{\"obsoleteQuestionIds\":[],\"newQuestions\":[{\"theme\":\"ERRORS\",\"label\":\""
                + tooLong + "\",\"rationale\":\"r\",\"suggestions\":[],\"defaultValue\":\"\",\"dependsOnQuestionIds\":[]}]}";
        assertThatThrownBy(() -> LlmOutputs.reassess(reassess)).isInstanceOf(IllegalArgumentException.class);
    }

    static String withDeviations(String deviations) {
        return WITHOUT.substring(0, WITHOUT.lastIndexOf('}')) + ",\"sourceDeviations\":" + deviations + "}";
    }

    /** T042 (016) : écarts entre sources facultatifs à la lecture, redemandés hors bornes. */
    @Test
    void ecartsEntreSourcesFacultatifsEtBornes() {
        assertThat(LlmOutputs.draft(WITHOUT).sourceDeviations()).isEmpty();
        assertThat(LlmOutputs.draft(withDeviations("""
                [{"referenceRef":"docs:x/Demande.docx","interpretationRef":"https://wiki/p?pageId=1","difference":"500 contre 1 000."}]"""))
                .sourceDeviations()).singleElement().satisfies(d -> assertThat(d.difference()).isEqualTo("500 contre 1 000."));
        for (String bad : new String[] {
                "[{\"referenceRef\":\"\",\"interpretationRef\":\"b\",\"difference\":\"d\"}]",
                "[{\"referenceRef\":\"a\",\"interpretationRef\":\"b\",\"difference\":\"" + "x".repeat(1_001) + "\"}]",
                "[" + String.join(",", java.util.Collections.nCopies(11,
                        "{\"referenceRef\":\"a\",\"interpretationRef\":\"b\",\"difference\":\"d\"}")) + "]"}) {
            assertThatThrownBy(() -> LlmOutputs.draft(withDeviations(bad))).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
