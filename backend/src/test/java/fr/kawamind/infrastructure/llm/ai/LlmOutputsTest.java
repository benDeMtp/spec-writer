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
}
