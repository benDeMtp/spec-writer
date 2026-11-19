package fr.kawamind.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.knowledge.SynchronizeKnowledge.Mode;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import fr.kawamind.domain.sync.SourceChange;
import fr.kawamind.domain.sync.SourceSyncResult;
import fr.kawamind.domain.sync.SyncInterval;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryKnowledgeSources;
import fr.kawamind.support.InMemorySourceSync;
import fr.kawamind.support.JiraWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T024 : échéances des sources, une à la fois par projet (017, research.md R4). */
class AutoSyncTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Duration MINUTE = Duration.ofMinutes(1);

    final JiraWorld world = new JiraWorld();
    final InMemorySourceSync syncs = new InMemorySourceSync();
    final List<Runnable> tasks = new ArrayList<>();
    UUID doc;
    Indexations indexations;
    AutoSync auto;

    @BeforeEach
    void setUp() {
        world.grant(ConsentService.JIRA).configureJira();
        doc = UUID.randomUUID();
        world.docSources.save(DocSource.create(doc, P, DocSource.Kind.URL, "https://doc.example.com/"));
        SynchronizeKnowledge sync = new SynchronizeKnowledge(world.projects, new InMemoryKnowledgeSources(),
                world.docSources, world.docs, world.folder, world.fileStates, (w, o) -> null, world.jira, world.tracker,
                world.ticketLinks, new InMemoryKnowledgeIndex(), new FakeEmbedding(), world.clock);
        indexations = new Indexations(sync, world.projects, tasks::add, world.clock, true, syncs);
        auto = new AutoSync(syncs, indexations, world.clock, MINUTE);
    }

    Instant now() {
        return world.clock.now();
    }

    @Test
    void sansDelaiRienNePart() {
        auto.tick();
        assertThat(tasks).isEmpty();
    }

    @Test
    void laSourceEchueLaPlusAncienneSeuleEnIncremental() {
        syncs.setInterval(P, IndexationScope.JIRA, SyncInterval.M15);
        syncs.record(P, IndexationScope.JIRA, SourceSyncResult.success(now().minus(Duration.ofMinutes(20)), SourceChange.NONE));
        syncs.setInterval(P, doc.toString(), SyncInterval.M30);
        syncs.record(P, doc.toString(), SourceSyncResult.success(now().minus(Duration.ofHours(2)), SourceChange.NONE));
        auto.tick();
        assertThat(tasks).hasSize(1);
        assertThat(indexations.current(P)).get().satisfies(s -> {
            assertThat(s.mode()).isEqualTo(Mode.INCREMENTAL);
            assertThat(s.sources()).containsExactly(doc.toString());
        });
        // projet occupé : rien d'autre ne part, rien n'est mis en file (FR-010)
        auto.tick();
        assertThat(tasks).hasSize(1);
        tasks.removeFirst().run();
        assertThat(syncs.find(P, doc.toString()).orElseThrow().last().at()).isEqualTo(now());
        // la suivante part au passage suivant
        auto.tick();
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::sources)
                .isEqualTo(List.of(IndexationScope.JIRA));
    }

    @Test
    void pasEncoreEchueOuDesactivee() {
        syncs.setInterval(P, IndexationScope.JIRA, SyncInterval.H1);
        syncs.record(P, IndexationScope.JIRA, SourceSyncResult.success(now().minus(Duration.ofMinutes(59)), SourceChange.NONE));
        syncs.record(P, doc.toString(), SourceSyncResult.success(now().minus(Duration.ofDays(9)), SourceChange.NONE));
        auto.tick();
        assertThat(tasks).isEmpty();
    }

    @Test
    void jamaisSynchroniseeEstEchueDesQuUnDelaiEstDonne() {
        syncs.setInterval(P, doc.toString(), SyncInterval.D1);
        auto.tick();
        assertThat(tasks).hasSize(1);
    }

    @Test
    void prerequisManquantConsigneSansAppelNiRelanceImmediate() {
        world.settings.deleteJira();
        syncs.setInterval(P, IndexationScope.JIRA, SyncInterval.M15);
        auto.tick();
        assertThat(tasks).isEmpty();
        assertThat(syncs.find(P, IndexationScope.JIRA).orElseThrow().last()).satisfies(r -> {
            assertThat(r.outcome()).isEqualTo(SourceSyncResult.Outcome.ERROR);
            assertThat(r.errorCode()).isEqualTo("SOURCE_UNAVAILABLE");
        });
        syncs.recorded.clear();
        auto.tick();
        assertThat(syncs.recorded).isEmpty();
    }

    @Test
    void lesProjetsPartentChacunAvecLeurSource() {
        UUID other = UUID.randomUUID();
        world.projects.save(Project.create(other, ProjectName.of("Autre"), null, null, now()));
        UUID otherDoc = UUID.randomUUID();
        world.docSources.save(DocSource.create(otherDoc, other, DocSource.Kind.URL, "https://autre.example.com/"));
        syncs.setInterval(P, doc.toString(), SyncInterval.M15);
        syncs.setInterval(other, otherDoc.toString(), SyncInterval.M15);
        auto.tick();
        assertThat(tasks).hasSize(2);
    }

    @Test
    void uneSourceEnEchecNArretePasLesAutres() {
        UUID missing = UUID.randomUUID();
        syncs.setInterval(P, missing.toString(), SyncInterval.M15);
        syncs.setInterval(P, doc.toString(), SyncInterval.M15);
        syncs.record(P, doc.toString(), SourceSyncResult.success(now().minus(Duration.ofMinutes(16)), SourceChange.NONE));
        auto.tick();
        assertThat(syncs.find(P, missing.toString()).orElseThrow().last().outcome())
                .isEqualTo(SourceSyncResult.Outcome.ERROR);
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::sources)
                .isEqualTo(List.of(doc.toString()));
    }

    @Test
    void uneIndexationAnnuleeOuEnEchecConsigneSesSources() {
        indexations.start(P, Mode.INCREMENTAL, List.of(doc.toString()));
        indexations.cancel(P);
        assertThat(syncs.find(P, doc.toString()).orElseThrow().last()).satisfies(r -> {
            assertThat(r.outcome()).isEqualTo(SourceSyncResult.Outcome.ERROR);
            assertThat(r.errorCode()).isEqualTo("CANCELLED");
        });
    }
}
