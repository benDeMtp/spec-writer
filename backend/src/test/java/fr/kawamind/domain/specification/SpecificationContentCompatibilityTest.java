package fr.kawamind.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 021, research.md R1 : une version écrite avant les ancres et les points ouverts se lit toujours. */
class SpecificationContentCompatibilityTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    static final String BEFORE_021 = """
            {"title":"Avoirs","summary":"Émission des avoirs.","details":"",
             "useCases":[{"code":"UC-01","title":"Émettre","actor":"gestionnaire","goal":"Créditer",
               "preconditions":[],"nominalFlow":["Ouvrir la facture"],"alternatives":[],
               "source":{"kind":"NEED_EXCERPT","questionId":null,"excerpt":"avoir","ref":null}}],
             "scenarios":[],
             "assumptions":[{"code":"H-01","statement":"Le montant est TTC.","origin":"TOOL_INFERENCE","questionId":null}],
             "requirements":[],"adrDeviations":[],"story":null,"splitProposal":[],
             "sourceDeviations":[
               {"reference":{"ref":"https://wiki/1","origin":null,"title":null,"updatedAt":null},
                "interpretation":{"ref":"https://wiki/2","origin":null,"title":null,"updatedAt":null},
                "difference":"Le plafond diffère."},
               {"reference":{"ref":"https://wiki/3","origin":null,"title":null,"updatedAt":null},
                "interpretation":{"ref":"https://wiki/4","origin":null,"title":null,"updatedAt":null},
                "difference":"Le délai diffère."}]}
            """;

    @Test
    void versionAnterieureLisible() {
        SpecificationContent c = JSON.readValue(BEFORE_021, SpecificationContent.class);
        assertThat(c.openQuestions()).isEmpty();
        assertThat(c.assumptions().getFirst().anchor()).isNull();
        assertThat(c.assumptions().getFirst().fromCode()).isNull();
        assertThat(c.sourceDeviations()).extracting(SourceDeviation::code).containsExactly("EC-01", "EC-02");
        assertThat(c.sourceDeviations().getFirst().anchor()).isNull();
    }

    @Test
    void allerRetour() {
        SpecificationContent c = JSON.readValue(BEFORE_021, SpecificationContent.class)
                .withOpenQuestions(List.of(new OpenQuestion("Q-01", "Qui valide ?", "Paul D.", null,
                        new Anchor("UC-01", 1, false), null)));
        SpecificationContent back = JSON.readValue(JSON.writeValueAsString(c), SpecificationContent.class);
        assertThat(back).isEqualTo(c);
    }
}
