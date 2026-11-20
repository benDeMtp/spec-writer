package fr.kawamind.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.knowledge.SynchronizeKnowledge.Mode;
import fr.kawamind.domain.project.Project;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryKnowledgeSources;
import fr.kawamind.support.JiraWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T003 : les indexations en cours, pour Quitter (019, research.md R4). */
class IndexationsRunningTest {

    final JiraWorld world = new JiraWorld();
    final List<Runnable> tasks = new ArrayList<>();
    Indexations indexations;

    @BeforeEach
    void setUp() {
        SynchronizeKnowledge sync = new SynchronizeKnowledge(world.projects, new InMemoryKnowledgeSources(),
                world.docSources, world.docs, world.folder, world.fileStates, (w, o) -> null, world.jira, world.tracker,
                world.ticketLinks, new InMemoryKnowledgeIndex(), new FakeEmbedding(), world.clock);
        indexations = new Indexations(sync, world.projects, tasks::add, world.clock, false);
    }

    @Test
    void rienNeTourne() {
        assertThat(indexations.running()).isEmpty();
    }

    @Test
    void lIndexationLanceeEstListeeAvecSonProjet() {
        indexations.start(Project.DEFAULT_ID, Mode.FULL);

        assertThat(indexations.running()).extracting(Indexations.Snapshot::projectId).containsExactly(Project.DEFAULT_ID);
    }

    @Test
    void toutAnnulerVideLaListe() {
        indexations.start(Project.DEFAULT_ID, Mode.FULL);

        indexations.cancelAll();

        assertThat(indexations.running()).isEmpty();
        assertThat(indexations.current(Project.DEFAULT_ID)).get()
                .extracting(Indexations.Snapshot::status).isEqualTo(Indexations.Status.CANCELLED);
    }

    @Test
    void uneIndexationTermineeNEstPlusListee() {
        indexations.start(Project.DEFAULT_ID, Mode.FULL);
        tasks.forEach(Runnable::run);

        assertThat(indexations.running()).isEmpty();
    }
}
