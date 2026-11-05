package dev.specwriter.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.knowledge.FindRelatedIssues;
import dev.specwriter.application.knowledge.StoryFamily;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
    @Inject
    SessionRepository sessions;
    @Inject
    SpecificationRepository versions;
    @Inject
    LinkedTicketRepository tickets;
    @Inject
    StoryFamily families;
    @Inject
    JiraAccess jira;
    @Inject
    IssueTracker tracker;
    @ConfigProperty(name = "specwriter.knowledge.subtask-min-z")
    double minZ;

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

    /**
     * T135 : SC-004c sur le jeu fictif. La famille de la story liée n'entre ni dans la sélection ni
     * dans les tickets proches, et au moins 80 % des sous-tâches touchées par la nouvelle version
     * sont signalées.
     */
    @Test
    void sousTachesDeLaStorySignaleesSansJamaisEtreConcurrentes() throws Exception {
        try (ReferenceSet set = new ReferenceSet(Path.of(getClass().getResource("/knowledge").toURI()))) {
            assertThat(set.index().getString("status")).isEqualTo("SUCCEEDED");
            assertThat(set.subtaskCases).isNotEmpty();
            ReferenceSet.SubtaskWorld world = new ReferenceSet.SubtaskWorld(projects, sessions, versions, tickets, families,
                    index, embedding, related, jira, tracker, minZ);
            for (ReferenceSet.SubtaskCase c : set.subtaskCases) {
                ReferenceSet.SubtaskMeasure m = set.measureSubtasks(c, world);
                assertThat(m.familyInSelection()).as("famille de %s dans la sélection", c.story()).isEmpty();
                assertThat(m.familyInRelated()).as("famille de %s dans les tickets proches", c.story()).isEmpty();
                assertThat(m.flagged()).as("sous-tâches signalées pour %s : %s", c.story(), m.states())
                        .isGreaterThanOrEqualTo(0.8);
                assertThat(m.states()).as("états des sous-tâches de %s", c.story())
                        .containsExactlyInAnyOrderEntriesOf(c.expected());
            }
        }
    }
}
