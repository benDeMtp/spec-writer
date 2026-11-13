package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.conversation.TurnEvents.Event;
import dev.specwriter.application.sources.AnalyzePerimeter;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.conversation.TurnSource;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ConversationLlm.Plan;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.ModuleQualification;
import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.Qualification;
import dev.specwriter.domain.sources.SourceRoot;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.FakeLocalSearch;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.InMemoryPerimeters;
import dev.specwriter.support.InMemorySourceRoots;
import dev.specwriter.support.JiraWorld;
import dev.specwriter.support.ScriptedConversationLlm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 013, US2 : l'agent analyse le périmètre dans les sources du code (research.md R6, R7). */
class ConversePerimeterTest {

    static final UUID P = Project.DEFAULT_ID;
    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final InMemoryConversations conversations = new InMemoryConversations();
    final ScriptedConversationLlm llm = new ScriptedConversationLlm();
    final TurnEvents events = new TurnEvents(w.clock);
    final InMemorySourceRoots roots = new InMemorySourceRoots();
    final InMemoryPerimeters perimeters = new InMemoryPerimeters();
    final List<PerimeterTerms> searched = new ArrayList<>();
    List<SourceTree.ModuleHit> hits = List.of(hit("libs/common-contract", "ContractStatus", 3),
            hit("modules/facturation", "ContractStatus", 1));
    final SourceTree tree = new SourceTree() {
        @Override
        public Preview preview(SourceRoot root, Limits limits) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scan search(List<SourceRoot> declared, PerimeterTerms terms, Limits limits, BooleanSupplier cancelled) {
            searched.add(terms);
            return new Scan(hits, hits.stream().map(ModuleHit::module).toList(), Map.of(),
                    List.of(new PerimeterAnalysis.RootStatus(ROOT, "rv", PerimeterAnalysis.RootState.SCANNED)), 12, 0,
                    List.of(), null);
        }
    };
    Converse converse;
    Conversation conversation;

    static SourceTree.ModuleHit hit(String path, String term, int count) {
        Module module = new Module(ROOT, path, path.substring(path.lastIndexOf('/') + 1), ModuleKind.MAVEN, null);
        return new SourceTree.ModuleHit(module, Map.of(term, count),
                List.of(new Occurrence(path + "/A.java", 1, term, "class A { " + term + " s; }")));
    }

    @BeforeEach
    void setUp() {
        ConversationSearch search = new ConversationSearch(new InMemoryKnowledgeIndex(), new FakeEmbedding(),
                new FakeLocalSearch());
        AnalyzePerimeter analyze = new AnalyzePerimeter(roots, tree, llm, w.clock);
        converse = new Converse(w.projects, conversations, llm, search, DesignatedDocuments.NONE, ModelCapacities.UNKNOWN,
                w.policy, new OperationRegistry(), events, w.clock, w.ids, Runnable::run, null, analyze, perimeters);
        conversation = converse.create(P);
    }

    void declareRoot() {
        roots.save(new SourceRoot(ROOT, P, "/src/rv", ExclusionPatterns.NONE, Instant.EPOCH));
    }

    List<Event> seen(UUID turn) {
        List<Event> seen = new ArrayList<>();
        events.subscribe(turn, seen::add);
        return seen;
    }

    @Test
    void lAnalyseEstPublieePuisQualifieeEtSesModulesSontCitables() {
        declareRoot();
        llm.plans.add(Plan.perimeter(List.of("ContractStatus", "CONTRACT_STATUS")));
        llm.qualifications.add(List.of(new ModuleQualification(ROOT + "/libs/common-contract", Qualification.IMPACTED,
                "Définit l'énumération.")));
        llm.answers.add(List.of("Modules : common-contract [1], facturation [2]."));

        Converse.Started started = converse.send(conversation.id(), "Quels modules touche le statut SUSPENDU ?");

        assertThat(llm.planRequests.getFirst().perimeterAvailable()).isTrue();
        // une seule analyse par réponse : le plan suivant ne la propose plus
        assertThat(llm.planRequests.get(1).perimeterAvailable()).isFalse();
        assertThat(searched).singleElement().extracting(PerimeterTerms::values)
                .isEqualTo(List.of("ContractStatus", "CONTRACT_STATUS"));
        List<PerimeterAnalysis> saved = perimeters.list(started.turnId());
        assertThat(saved).singleElement().satisfies(a -> {
            assertThat(a.ordinal()).isEqualTo(1);
            assertThat(a.modules()).extracting(m -> m.qualification())
                    .containsExactly(Qualification.IMPACTED, Qualification.UNQUALIFIED);
        });
        List<Event> events = seen(started.turnId());
        List<Event.Perimeter> published = events.stream().filter(Event.Perimeter.class::isInstance)
                .map(Event.Perimeter.class::cast).toList();
        assertThat(published).hasSize(2);
        assertThat(published.getFirst().analysis().qualified()).isFalse();
        assertThat(published.get(1).analysis().qualified()).isTrue();
        assertThat(published.getFirst().analysis().ordinal()).isEqualTo(published.get(1).analysis().ordinal());
        assertThat(published.getFirst().toString()).doesNotContain("ContractStatus");

        List<TurnSource> sources = llm.answerRequests.getFirst().sources();
        assertThat(sources).extracting(TurnSource::kind).containsOnly(SourceKind.MODULE);
        assertThat(sources).extracting(TurnSource::ref).containsExactly("module:" + ROOT + "/libs/common-contract",
                "module:" + ROOT + "/modules/facturation");
        assertThat(sources.getFirst().text()).contains("Qualification : impacté").contains("Définit l'énumération.")
                .doesNotContain("class A");
        assertThat(conversations.sources(started.turnId())).hasSize(2);
        assertThat(conversations.messages(conversation.id()).get(1).state()).isEqualTo(MessageState.COMPLETE);
    }

    @Test
    void uneSecondeDemandeDansLeMemeTourVautUneReponse() {
        declareRoot();
        llm.plans.add(Plan.perimeter(List.of("ContractStatus")));
        llm.plans.add(Plan.perimeter(List.of("Autre")));
        llm.plans.add(new Plan(dev.specwriter.domain.port.ConversationLlm.Action.SEARCH, "jamais", Set.of()));

        converse.send(conversation.id(), "Périmètre ?");

        assertThat(searched).hasSize(1);
        assertThat(llm.planRequests).hasSize(2);
        assertThat(llm.answerRequests).hasSize(1);
    }

    @Test
    void sansRacineLaReponseInviteADeclarerLesSources() {
        llm.plans.add(Plan.perimeter(List.of("ContractStatus")));

        Converse.Started started = converse.send(conversation.id(), "Périmètre ?");

        assertThat(llm.planRequests.getFirst().perimeterAvailable()).isFalse();
        assertThat(searched).isEmpty();
        assertThat(perimeters.list(started.turnId())).singleElement()
                .extracting(PerimeterAnalysis::unavailable).isEqualTo(PerimeterAnalysis.Unavailable.NO_ROOTS);
        assertThat(llm.answerRequests.getFirst().perimeterUnavailable()).isTrue();
        assertThat(seen(started.turnId())).filteredOn(Event.Perimeter.class::isInstance).hasSize(1);
    }

    @Test
    void uneQualificationEnEchecLaisseLAnalyseEtLaReponseALieu() {
        declareRoot();
        llm.plans.add(Plan.perimeter(List.of("ContractStatus")));
        llm.qualifyFailure = new DomainException(ErrorCode.LLM_UNAVAILABLE);

        Converse.Started started = converse.send(conversation.id(), "Périmètre ?");

        assertThat(perimeters.list(started.turnId())).singleElement()
                .satisfies(a -> assertThat(a.modules()).hasSize(2).allMatch(m -> m.qualification() == Qualification.UNQUALIFIED));
        assertThat(llm.answerRequests).hasSize(1);
        ConversationMessage answer = conversations.messages(conversation.id()).get(1);
        assertThat(answer.state()).isEqualTo(MessageState.COMPLETE);
    }

    @Test
    void lesOrdinauxSuiventLesRecherchesEtLaRelanceSignaleLesNouveauxModules() {
        declareRoot();
        llm.plans.add(new Plan(dev.specwriter.domain.port.ConversationLlm.Action.SEARCH, "statut contrat", Set.of()));
        llm.plans.add(Plan.perimeter(List.of("ContractStatus")));
        Converse.Started first = converse.send(conversation.id(), "Périmètre ?");
        assertThat(perimeters.list(first.turnId())).singleElement().extracting(PerimeterAnalysis::ordinal).isEqualTo(2);
        perimeters.conversationOf.put(first.turnId(), conversation.id());

        hits = List.of(hit("libs/common-contract", "ContractStatus", 3), hit("modules/batch-suspension",
                "contract_suspended", 1));
        llm.plans.add(Plan.perimeter(List.of("ContractStatus", "contract_suspended")));
        Converse.Started second = converse.send(conversation.id(), "Cherche aussi contract_suspended");

        assertThat(llm.planRequests.get(llm.planRequests.size() - 2).lastPerimeterTerms()).containsExactly("ContractStatus");
        assertThat(perimeters.list(second.turnId())).singleElement().satisfies(a -> assertThat(a.modules())
                .extracting(m -> m.module().name() + ":" + m.isNew())
                .containsExactly("common-contract:false", "batch-suspension:true"));
    }

    @Test
    void sansTermeValideLAgentRepondSansAnalyse() {
        declareRoot();
        llm.plans.add(Plan.perimeter(List.of("x")));

        Converse.Started started = converse.send(conversation.id(), "Périmètre ?");

        assertThat(searched).isEmpty();
        assertThat(perimeters.list(started.turnId())).isEmpty();
        assertThat(llm.answerRequests).hasSize(1);
    }
}
