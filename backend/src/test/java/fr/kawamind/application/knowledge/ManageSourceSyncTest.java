package fr.kawamind.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.sync.SourceChange;
import fr.kawamind.domain.sync.SourceSyncResult;
import fr.kawamind.domain.sync.SyncInterval;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryKnowledgeSources;
import fr.kawamind.support.InMemorySourceSync;
import fr.kawamind.support.JiraWorld;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T025 : délais et bilans des sources d'un projet (017, contracts/rest-api.md). */
class ManageSourceSyncTest {

    static final UUID P = Project.DEFAULT_ID;

    final JiraWorld world = new JiraWorld();
    final InMemorySourceSync syncs = new InMemorySourceSync();
    UUID doc;
    ManageSourceSync manage;

    @BeforeEach
    void setUp() {
        world.grant(ConsentService.JIRA).configureJira();
        doc = UUID.randomUUID();
        world.docSources.save(DocSource.create(doc, P, DocSource.Kind.URL, "https://doc.example.com/"));
        SynchronizeKnowledge sync = new SynchronizeKnowledge(world.projects, new InMemoryKnowledgeSources(),
                world.docSources, world.docs, world.folder, world.fileStates, (w, o) -> null, world.jira, world.tracker,
                world.ticketLinks, new InMemoryKnowledgeIndex(), new FakeEmbedding(), world.clock);
        Indexations indexations = new Indexations(sync, world.projects, Runnable::run, world.clock, true, syncs);
        manage = new ManageSourceSync(syncs, indexations, Duration.ofMinutes(1));
    }

    @Test
    void uneEntreeParSourceDansLOrdreDeLaReindexation() {
        syncs.record(P, IndexationScope.JIRA, SourceSyncResult.success(world.clock.now(), new SourceChange(1, 2, 0)));
        assertThat(manage.list(P)).extracting(ManageSourceSync.State::sourceKey)
                .containsExactly(IndexationScope.JIRA, doc.toString());
        ManageSourceSync.State jira = manage.list(P).getFirst();
        assertThat(jira.label()).isEqualTo("Jira PROJ");
        assertThat(jira.interval()).isEqualTo(SyncInterval.OFF);
        assertThat(jira.last().change()).isEqualTo(new SourceChange(1, 2, 0));
        assertThat(jira.lastSuccessAt()).isEqualTo(world.clock.now());
        assertThat(jira.nextRunAt()).isNull();
        assertThat(manage.list(P).get(1).last()).isNull();
    }

    @Test
    void reglerUnDelaiCalculeLaProchaineSynchronisation() {
        syncs.record(P, doc.toString(), SourceSyncResult.success(world.clock.now(), SourceChange.NONE));
        ManageSourceSync.State state = manage.setInterval(P, doc.toString(), 60);
        assertThat(state.interval()).isEqualTo(SyncInterval.H1);
        assertThat(state.nextRunAt()).isEqualTo(world.clock.now().plus(Duration.ofHours(1)));
        assertThat(manage.setInterval(P, doc.toString(), null).nextRunAt()).isNull();
    }

    @Test
    void sourceInconnueOuDelaiHorsListe() {
        assertThatThrownBy(() -> manage.setInterval(P, UUID.randomUUID().toString(), 15))
                .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> manage.setInterval(P, doc.toString(), 20)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> manage.list(UUID.randomUUID())).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }
}
