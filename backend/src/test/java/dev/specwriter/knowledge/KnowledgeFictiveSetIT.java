package dev.specwriter.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.knowledge.FindRelatedIssues;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * T118 : jeu de référence fictif ({@code BTR/knowledge}) dans {@code verify}, sans LLM. Pour chaque
 * besoin : les tickets attendus sont parmi les cinq premiers candidats, les ADR attendus sont
 * transmis (jamais un ADR remplacé ou obsolète), les modules attendus sont parmi les candidats de
 * l'analyse, et les flux manquants et efforts de déploiement calculés sont exactement ceux attendus.
 */
@QuarkusTest
class KnowledgeFictiveSetIT {

    @Inject
    ProjectRepository projects;
    @Inject
    KnowledgeIndex index;
    @Inject
    EmbeddingPort embedding;
    @Inject
    FindRelatedIssues related;

    @Test
    void chaqueBesoinRetrouveCeQuiEstAttendu() throws Exception {
        try (ReferenceSet set = new ReferenceSet(Path.of(getClass().getResource("/knowledge").toURI()))) {
            JsonPath state = set.index();
            assertThat(state.getString("status")).isEqualTo("SUCCEEDED");
            assertThat(state.getList("errors")).isEmpty();
            assertThat(set.cases).isNotEmpty();
            for (ReferenceSet.Case c : set.cases) {
                ReferenceSet.Measure m = set.measure(c, projects, index, embedding, related);
                assertThat(m.missedIssues()).as("tickets attendus parmi les %d premiers pour « %s » : %s",
                        ReferenceSet.SHOWN, c.need(), m.candidates()).isEmpty();
                assertThat(m.missedAdrs()).as("ADR attendus pour « %s » : %s", c.need(), m.adrs()).isEmpty();
                assertThat(m.retiredAdrs()).as("ADR remplacés ou obsolètes pour « %s »", c.need()).isEmpty();
                assertThat(m.missedModules()).as("modules attendus parmi les candidats pour « %s » : %s", c.need(),
                        m.moduleCandidates()).isEmpty();
                assertThat(m.missingFlows()).as("flux manquants pour « %s »", c.need())
                        .containsExactlyInAnyOrderElementsOf(c.expectedMissingFlows());
                assertThat(m.efforts()).as("efforts de déploiement pour « %s »", c.need())
                        .containsExactlyInAnyOrderElementsOf(c.expectedEfforts());
            }
        }
    }
}
