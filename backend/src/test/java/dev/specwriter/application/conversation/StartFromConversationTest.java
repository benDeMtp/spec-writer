package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.PerimeterSnapshot;
import dev.specwriter.domain.sources.Reason;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.InMemoryPerimeters;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySessions;
import dev.specwriter.support.SequentialIdGenerator;
import dev.specwriter.support.StubLlm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 013, US5 : la spécification rédigée depuis la discussion reprend la dernière analyse de périmètre. */
class StartFromConversationTest {

    static final Instant T = Instant.parse("2026-11-11T09:00:00Z");
    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final String NEED = "Le gestionnaire doit pouvoir suspendre un contrat d'abonnement et en reprendre la facturation.";

    final InMemorySessions sessions = new InMemorySessions();
    final InMemoryConversations conversations = new InMemoryConversations();
    final InMemoryPerimeters perimeters = new InMemoryPerimeters();
    final InMemoryConsents consents = new InMemoryConsents();
    final StubLlm llm = new StubLlm();
    final FixedClock clock = new FixedClock();
    final SequentialIdGenerator ids = new SequentialIdGenerator();
    StartFromConversation start;
    Conversation conversation;

    @BeforeEach
    void setUp() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        InMemoryProjects projects = new InMemoryProjects();
        StartSession startSession = new StartSession(sessions, projects, llm, new ConsentPolicy(consents), clock, ids);
        start = new StartFromConversation(conversations, projects, startSession, c -> List.of(), perimeters, clock);
        conversation = new Conversation(UUID.randomUUID(), Project.DEFAULT_ID, "Suspension", T, T);
        conversations.save(conversation);
        llm.questions.add(StubLlm.none());
    }

    static PerimeterAnalysis analysis(int ordinal, String... paths) {
        List<PerimeterModule> modules = java.util.Arrays.stream(paths).map(p -> PerimeterModule.found(
                new Module(ROOT, p, p.substring(p.lastIndexOf('/') + 1), ModuleKind.MAVEN, null),
                List.of(new Reason.Term("ContractStatus", 1)), 1, List.of())).toList();
        return PerimeterAnalysis.complete(ordinal, List.of("ContractStatus"), List.of(),
                List.of(new PerimeterAnalysis.RootStatus(ROOT, "rv", PerimeterAnalysis.RootState.SCANNED)), 10, 0, List.of(),
                5, modules, List.of());
    }

    @Test
    void laDerniereAnalyseEstCopieeDansLaSession() {
        ConversationMessage answer = ConversationMessage.agentStarted(UUID.randomUUID(), conversation.id(), 2, T);
        perimeters.conversationOf.put(answer.id(), conversation.id());
        perimeters.save(answer.id(), analysis(1, "libs/a"));
        perimeters.save(answer.id(), analysis(2, "libs/a", "modules/b"));

        SessionSnapshot started = start.start(conversation.id(), new NeedDraft(NEED, List.of()));

        assertThat(perimeters.snapshot(started.session().id())).get()
                .extracting(s -> s.modules().stream().map(PerimeterSnapshot.Entry::name).toList())
                .isEqualTo(List.of("a", "b"));
    }

    @Test
    void sansAnalyseLaSessionNAPasDePerimetre() {
        SessionSnapshot started = start.start(conversation.id(), new NeedDraft(NEED, List.of()));
        assertThat(perimeters.snapshot(started.session().id())).isEmpty();
    }
}
