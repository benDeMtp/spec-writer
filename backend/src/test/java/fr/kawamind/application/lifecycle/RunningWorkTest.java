package fr.kawamind.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.conversation.TurnEvents;
import fr.kawamind.application.knowledge.Indexations;
import fr.kawamind.application.knowledge.SynchronizeKnowledge;
import fr.kawamind.application.knowledge.SynchronizeKnowledge.Mode;
import fr.kawamind.domain.conversation.Conversation;
import fr.kawamind.domain.project.Project;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.InMemoryConversations;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryKnowledgeSources;
import fr.kawamind.support.JiraWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T009 : inventaire des travaux en cours, nommés sans contenu saisi (019, contracts/rest-api.md). */
class RunningWorkTest {

    final JiraWorld world = new JiraWorld();
    final InMemoryConversations conversations = new InMemoryConversations();
    final TurnEvents turns = new TurnEvents(world.clock);
    final LongWorkTracker generations = new LongWorkTracker();
    final List<Runnable> tasks = new ArrayList<>();
    final UUID conversation = UUID.randomUUID();
    Indexations indexations;
    RunningWork work;

    @BeforeEach
    void setUp() {
        SynchronizeKnowledge sync = new SynchronizeKnowledge(world.projects, new InMemoryKnowledgeSources(),
                world.docSources, world.docs, world.folder, world.fileStates, (w, o) -> null, world.jira, world.tracker,
                world.ticketLinks, new InMemoryKnowledgeIndex(), new FakeEmbedding(), world.clock);
        indexations = new Indexations(sync, world.projects, tasks::add, world.clock, false);
        conversations.save(new Conversation(conversation, Project.DEFAULT_ID, "Facturation des avoirs",
                world.clock.now(), world.clock.now()));
        work = new RunningWork(indexations, turns, generations, world.projects, conversations);
    }

    @Test
    void rienNeTourne() {
        assertThat(work.items()).isEmpty();
    }

    @Test
    void lesTroisSourcesSontNommeesParProjetEtConversation() {
        indexations.start(Project.DEFAULT_ID, Mode.FULL);
        turns.open(UUID.randomUUID(), TurnEvents.Kind.AGENT_REPLY, conversation);
        turns.open(UUID.randomUUID(), TurnEvents.Kind.REVISION, conversation);
        generations.begin(WorkKind.GENERATION, Project.DEFAULT_ID);

        assertThat(work.items()).containsExactlyInAnyOrder(
                new RunningWork.Item(WorkKind.INDEXATION, Project.DEFAULT_NAME, null),
                new RunningWork.Item(WorkKind.AGENT_REPLY, Project.DEFAULT_NAME, "Facturation des avoirs"),
                new RunningWork.Item(WorkKind.REVISION, Project.DEFAULT_NAME, "Facturation des avoirs"),
                new RunningWork.Item(WorkKind.GENERATION, Project.DEFAULT_NAME, null));
    }

    @Test
    void uneConversationOublieeResteNommeeSansTitre() {
        turns.open(UUID.randomUUID(), TurnEvents.Kind.AGENT_REPLY, UUID.randomUUID());

        assertThat(work.items()).containsExactly(new RunningWork.Item(WorkKind.AGENT_REPLY, null, null));
    }

    @Test
    void toutAnnulerInterromptIndexationsEtTours() {
        UUID turn = UUID.randomUUID();
        indexations.start(Project.DEFAULT_ID, Mode.FULL);
        turns.open(turn, TurnEvents.Kind.AGENT_REPLY, conversation);

        int interrupted = work.cancelAll();

        assertThat(interrupted).isEqualTo(2);
        assertThat(indexations.running()).isEmpty();
        assertThat(turns.cancelled(turn)).isTrue();
    }
}
